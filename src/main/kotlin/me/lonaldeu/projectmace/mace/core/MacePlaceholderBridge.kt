package me.lonaldeu.projectmace.mace.core

import me.lonaldeu.projectmace.mace.LegendaryMaceManager
import me.lonaldeu.projectmace.mace.api.LegendaryLooseMaceView
import me.lonaldeu.projectmace.mace.api.LegendaryMaceWielderView
import me.lonaldeu.projectmace.mace.api.LegendaryMaceApi
import me.lonaldeu.projectmace.mace.domain.model.MaceConstants
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.Context
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Unified placeholder bridge for both PlaceholderAPI and MiniPlaceholders.
 *
 * **Priority**: PlaceholderAPI is checked first. If PlaceholderAPI is not available,
 * falls back to MiniPlaceholders. Both can be registered simultaneously if desired,
 * but PlaceholderAPI takes priority for detection/availability checks.
 *
 * ## Integration Details
 *
 * The integration uses **reflection** to avoid compile-time dependencies:
 * - Loads PlaceholderAPI/MiniPlaceholders classes dynamically via [ClassLoader]
 * - Creates proxy objects for resolver interfaces
 * - Registers placeholders without direct API imports
 *
 * ## PlaceholderAPI Format
 * - Uses `%projectmace_<placeholder>%` format
 *
 * ## MiniPlaceholders Format
 * - Uses `<projectmace_<placeholder>>` format
 *
 * ## Global Placeholders (Server-wide)
 *
 * - `<projectmace_active_wielder_count>` - Number of active mace wielders
 * - `<projectmace_total_mace_count>` - Total maces in existence
 * - `<projectmace_max_mace_count>` - Maximum allowed maces
 * - `<projectmace_available_mace_slots>` - Available mace slots
 * - `<projectmace_wielder_names>` - Comma-separated wielder names
 * - `<projectmace_wielder_uuids>` - Comma-separated wielder UUIDs
 * - `<projectmace_wielder_timer_seconds_list>` - Wielders with remaining seconds
 * - `<projectmace_wielder_timer_formatted_list>` - Wielders with formatted time
 * - `<projectmace_wielder_last_kill_names>` - Last victim for each wielder
 * - `<projectmace_loose_mace_count>` - Number of dropped maces
 * - `<projectmace_loose_mace_locations>` - Dropped mace locations
 * - `<projectmace_loose_mace_details>` - Detailed loose mace info
 * - `<projectmace_loose_mace_world_counts>` - Maces per world
 * - `<projectmace_loose_mace_owner_names>` - Owners of dropped maces
 * - `<projectmace_loose_mace_next_despawn_seconds>` - Next despawn time (seconds)
 * - `<projectmace_loose_mace_next_despawn_formatted>` - Next despawn time (formatted)
 * - `<projectmace_loose_mace_latest_despawn_seconds>` - Latest despawn time (seconds)
 * - `<projectmace_loose_mace_latest_despawn_formatted>` - Latest despawn time (formatted)
 * - `<projectmace_has_available_mace_slot>` - Whether slots available
 *
 * ## Per-Player Audience Placeholders
 *
 * - `<projectmace_timer_seconds>` - Remaining bloodthirst seconds
 * - `<projectmace_timer_formatted>` - Remaining time (HH:MM:SS)
 * - `<projectmace_timer_short>` - Remaining time (short form, e.g., "30m")
 * - `<projectmace_timer_minutes>` - Remaining time in minutes
 * - `<projectmace_timer_hours>` - Remaining time in hours
 * - `<projectmace_timer_percent>` - Timer completion percentage
 * - `<projectmace_timer_state>` - Timer state (active, warning, critical, expired, inactive)
 * - `<projectmace_is_wielder>` - Whether player has mace
 * - `<projectmace_has_timer>` - Whether player's timer is active
 * - `<projectmace_is_timer_warning>` - Whether timer in warning range (5m-0)
 * - `<projectmace_is_timer_critical>` - Whether timer in critical range (1m-0)
 * - `<projectmace_is_timer_expired>` - Whether timer has expired
 * - `<projectmace_last_kill_name>` - Last victim name
 * - `<projectmace_last_kill_uuid>` - Last victim UUID
 * - `<projectmace_mace_uuid>` - Player's mace UUID
 * - `<projectmace_bloodthirst_elapsed_seconds>` - Time elapsed on bloodthirst timer
 * - `<projectmace_bloodthirst_elapsed_percent>` - Bloodthirst progress percentage
 * - `<projectmace_mace_hold_duration>` - Total hold time in readable format (e.g., "14h 32m")
 *
 * ## Proxy Pattern
 *
 * Uses Java's [Proxy.newProxyInstance] to dynamically implement resolver interfaces:
 *
 * ```kotlin
 * Proxy.newProxyInstance(loader, arrayOf(globalResolverClass)) { proxy, method, args ->
 *     when (method.name) {
 *         "tag" -> block(queue, context)  // Execute placeholder logic
 *         "toString" -> "ProjectMaceGlobalResolver"
 *         "hashCode" -> System.identityHashCode(proxy)
 *         "equals" -> proxy === args?.getOrNull(0)
 *         else -> null
 *     }
 * }
 * ```
 *
 * @see MiniPlaceholdersReflection
 */
class MacePlaceholderBridge(
    private val plugin: Plugin,
    private val maceManager: LegendaryMaceManager,
    private val bloodthirstDurationSeconds: () -> Long,
    private val warningThresholdSeconds: Double,
    private val criticalThresholdSeconds: Double
) {

    private var registeredMiniPlaceholdersExpansion: Any? = null
    private var registeredPlaceholderApiExpansion: Any? = null
    private var placeholderApiRegistered = false
    private var miniPlaceholdersRegistered = false

    /**
     * Registers placeholder expansions for available placeholder plugins.
     *
     * ## Priority Order
     * 1. PlaceholderAPI (if available) - checked and registered first
     * 2. MiniPlaceholders (if available) - registered as fallback/additional support
     *
     * Both can be registered simultaneously if both plugins are present,
     * allowing maximum compatibility with different plugins that use either system.
     *
     * ## Behavior
     * - Checks PlaceholderAPI first via [PluginManager.getPlugin]
     * - Then checks MiniPlaceholders via [PluginManager.getPlugin]
     * - Returns silently if neither is found
     * - Unregisters previously registered expansions if exists
     *
     * @see registerPlaceholderApi
     * @see registerMiniPlaceholders
     */
    fun registerPlaceholders() {
        val pluginManager = plugin.server.pluginManager
        
        // Try PlaceholderAPI first (priority)
        val placeholderApi = pluginManager.getPlugin("PlaceholderAPI")
        if (placeholderApi != null && placeholderApi.isEnabled) {
            registerPlaceholderApi(placeholderApi)
        } else {
            plugin.logger.fine("[Mace] PlaceholderAPI not detected")
        }
        
        // Also try MiniPlaceholders (can work alongside PAPI)
        val miniPlaceholders = pluginManager.getPlugin("MiniPlaceholders")
        if (miniPlaceholders != null && miniPlaceholders.isEnabled) {
            registerMiniPlaceholders(miniPlaceholders)
        } else {
            plugin.logger.fine("[Mace] MiniPlaceholders not detected")
        }
        
        if (!placeholderApiRegistered && !miniPlaceholdersRegistered) {
            plugin.logger.fine("[Mace] No placeholder plugins detected; skipping placeholder registration")
        }
    }

    /**
     * Registers PlaceholderAPI expansion.
     * The expansion class is in a separate file to isolate the PAPI dependency.
     * 
     * With Paper's new plugin loader in paper-plugin.yml,
     * our classloader can see PlaceholderAPI's classes directly.
     */
    private fun registerPlaceholderApi(placeholderApi: org.bukkit.plugin.Plugin) {
        val api = maceManager.getApi()
        val state = api.state

        try {
            // Unregister if already registered
            unregisterPlaceholderApiIfRegistered()

            // Create the expansion directly - classloader visibility is handled by paper-plugin.yml
            val expansion = me.lonaldeu.projectmace.mace.core.papi.ProjectMacePapiExpansion(
                expansionIdentifier = EXPANSION_NAME,
                expansionAuthor = plugin.pluginMeta.authors.joinToString(", ").ifBlank { plugin.name },
                expansionVersion = plugin.pluginMeta.version,
                requestHandler = { player, params -> handlePlaceholderRequest(player, params, state) }
            )

            val registered = expansion.register()
            if (registered) {
                registeredPlaceholderApiExpansion = expansion
                placeholderApiRegistered = true
                plugin.logger.info("PlaceholderAPI expansion registered")
            }
        } catch (e: NoClassDefFoundError) {
            plugin.logger.warning("Failed to register PlaceholderAPI expansion: PlaceholderAPI classes not found - ${e.message}")
            plugin.logger.fine(e.stackTraceToString())
        } catch (e: Exception) {
            plugin.logger.warning("Failed to register PlaceholderAPI expansion: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Unregisters PlaceholderAPI expansion if previously registered.
     */
    private fun unregisterPlaceholderApiIfRegistered() {
        val expansion = registeredPlaceholderApiExpansion as? me.lonaldeu.projectmace.mace.core.papi.ProjectMacePapiExpansion
        if (expansion != null) {
            try {
                expansion.unregister()
            } catch (e: Exception) { /* ignore */ }
            registeredPlaceholderApiExpansion = null
            placeholderApiRegistered = false
        }
    }

    /**
     * Handles placeholder requests for PlaceholderAPI.
     * Returns the placeholder value as a String, or null if unknown.
     */
    private fun handlePlaceholderRequest(
        player: OfflinePlayer?,
        params: String,
        state: me.lonaldeu.projectmace.mace.api.LegendaryMaceStateView
    ): String? {
        return when (params.lowercase()) {
            // === Global Placeholders ===
            "active_wielder_count" -> state.getActiveWielders().size.toString()
            "total_mace_count" -> state.maceCount().toString()
            "max_mace_count" -> state.getMaxLegendaryMaces().toString()
            "available_mace_slots" -> (state.getMaxLegendaryMaces() - state.maceCount()).coerceAtLeast(0).toString()
            "has_available_mace_slot" -> boolText(state.maceCount() < state.getMaxLegendaryMaces())
            
            "wielder_names" -> formatList(state.getActiveWielders().map { lookupPlayerName(it.playerUuid) })
            "wielder_uuids" -> formatList(state.getActiveWielders().map { it.playerUuid.toString() })
            
            "wielder_timer_seconds_list" -> {
                val now = nowSeconds()
                formatList(state.getActiveWielders().map { wielder ->
                    "${lookupPlayerName(wielder.playerUuid)}:${formatNumeric(remainingSeconds(wielder, now))}"
                })
            }
            "wielder_timer_formatted_list" -> {
                val now = nowSeconds()
                formatList(state.getActiveWielders().map { wielder ->
                    "${lookupPlayerName(wielder.playerUuid)}:${formatDuration(remainingSeconds(wielder, now))}"
                })
            }
            "wielder_last_kill_names" -> {
                formatList(state.getActiveWielders().map { wielder ->
                    "${lookupPlayerName(wielder.playerUuid)}:${lookupPlayerName(wielder.lastKillUuid)}"
                })
            }
            
            "loose_mace_count" -> state.getLooseMaces().size.toString()
            "loose_mace_locations" -> formatList(state.getLooseMaces().map { formatLooseMaceLocation(it) })
            "loose_mace_details" -> {
                val now = nowSeconds()
                formatList(state.getLooseMaces().map { formatLooseMaceDetail(it, now) })
            }
            "loose_mace_world_counts" -> {
                formatList(state.getLooseMaces()
                    .groupBy { it.location.world?.name ?: "unknown" }
                    .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                    .map { (world, entries) -> "$world:${entries.size}" })
            }
            "loose_mace_owner_names" -> formatList(state.getLooseMaces().mapNotNull { it.originalOwnerUuid }.map { lookupPlayerName(it) })
            "loose_mace_next_despawn_seconds" -> formatNumeric(nextDespawnSeconds(state.getLooseMaces()))
            "loose_mace_next_despawn_formatted" -> formatDuration(nextDespawnSeconds(state.getLooseMaces()))
            "loose_mace_latest_despawn_seconds" -> formatNumeric(latestDespawnSeconds(state.getLooseMaces()))
            "loose_mace_latest_despawn_formatted" -> formatDuration(latestDespawnSeconds(state.getLooseMaces()))

            // === Per-Player Placeholders ===
            "timer_seconds" -> formatNumeric(player?.uniqueId?.let { state.getRemainingBloodthirstSeconds(it) })
            "timer_formatted" -> formatDuration(player?.uniqueId?.let { state.getRemainingBloodthirstSeconds(it) })
            "timer_short" -> formatShortDuration(player?.uniqueId?.let { state.getRemainingBloodthirstSeconds(it) })
            "timer_minutes" -> {
                val seconds = player?.uniqueId?.let { state.getRemainingBloodthirstSeconds(it) }
                formatNumeric(seconds?.div(60.0)?.coerceAtLeast(0.0) ?: 0.0)
            }
            "timer_hours" -> {
                val seconds = player?.uniqueId?.let { state.getRemainingBloodthirstSeconds(it) }
                formatNumeric(seconds?.div(3600.0)?.coerceAtLeast(0.0) ?: 0.0)
            }
            "timer_percent" -> {
                val seconds = player?.uniqueId?.let { state.getRemainingBloodthirstSeconds(it) }
                "%.2f".format(Locale.US, if (seconds != null) (seconds / bloodthirstDurationSeconds()) * 100.0 else 0.0)
            }
            "timer_state" -> resolveTimerState(player?.uniqueId?.let { state.getRemainingBloodthirstSeconds(it) })
            "is_wielder" -> boolText(player?.uniqueId?.let { state.findWielder(it) != null } ?: false)
            "has_timer" -> boolText(player?.uniqueId?.let { state.getRemainingBloodthirstSeconds(it) != null } ?: false)
            "is_timer_warning" -> {
                val seconds = player?.uniqueId?.let { state.getRemainingBloodthirstSeconds(it) }
                boolText(seconds != null && seconds > 0 && seconds <= warningThresholdSeconds)
            }
            "is_timer_critical" -> {
                val seconds = player?.uniqueId?.let { state.getRemainingBloodthirstSeconds(it) }
                boolText(seconds != null && seconds > 0 && seconds <= criticalThresholdSeconds)
            }
            "is_timer_expired" -> {
                val seconds = player?.uniqueId?.let { state.getRemainingBloodthirstSeconds(it) }
                boolText(seconds != null && seconds <= 0)
            }
            "last_kill_name" -> lookupPlayerName(player?.uniqueId?.let { state.findWielder(it) }?.lastKillUuid)
            "last_kill_uuid" -> player?.uniqueId?.let { state.findWielder(it) }?.lastKillUuid?.toString() ?: "none"
            "mace_uuid" -> player?.uniqueId?.let { state.findWielder(it) }?.maceUuid?.toString() ?: "none"
            "bloodthirst_elapsed_seconds" -> {
                val remaining = player?.uniqueId?.let { state.getRemainingBloodthirstSeconds(it) }
                formatNumeric(if (remaining != null) (bloodthirstDurationSeconds() - remaining).coerceAtLeast(0.0) else 0.0)
            }
            "bloodthirst_elapsed_percent" -> {
                val remaining = player?.uniqueId?.let { state.getRemainingBloodthirstSeconds(it) }
                val elapsed = if (remaining != null) ((bloodthirstDurationSeconds() - remaining) / bloodthirstDurationSeconds()) * 100.0 else 0.0
                "%.2f".format(Locale.US, elapsed.coerceIn(0.0, 100.0))
            }
            "mace_hold_duration" -> {
                val wielder = player?.uniqueId?.let { state.findWielder(it) }
                formatHoldDuration(if (wielder != null) 0L else 0L)
            }

            else -> null
        }
    }

    /**
     * Registers MiniPlaceholders expansion using reflection.
     */
    private fun registerMiniPlaceholders(miniPlaceholders: org.bukkit.plugin.Plugin) {
        val api = maceManager.getApi()
        val reflection = MiniPlaceholdersReflection.forLoader(miniPlaceholders.javaClass.classLoader)

        runCatching {
            reflection.unregisterIfRegistered(registeredMiniPlaceholdersExpansion)

            val builder = reflection.newBuilder(EXPANSION_NAME)
            val meta = plugin.pluginMeta
            reflection.setAuthor(builder, meta.authors.joinToString(", ").ifBlank { plugin.name })
            reflection.setVersion(builder, meta.version)

            registerGlobalPlaceholders(reflection, builder, api)
            registerAudiencePlaceholders(reflection, builder, api)

            val expansion = reflection.build(builder)
            reflection.register(expansion)
            registeredMiniPlaceholdersExpansion = expansion
            miniPlaceholdersRegistered = true
            plugin.logger.info("MiniPlaceholders expansion registered")
        }.onFailure { error ->
            plugin.logger.warning("Failed to register MiniPlaceholders expansion: ${error.message}")
            plugin.logger.fine(error.stackTraceToString())
        }
    }

    /**
     * Registers server-wide (global) placeholders that don't require player context.
     *
     * These placeholders query the server state and are executed once per placeholder request.
     *
     * @param reflection The MiniPlaceholders reflection wrapper
     * @param builder The expansion builder
     * @param api The ProjectMace API for accessing mace state
     */
    private fun registerGlobalPlaceholders(
        reflection: MiniPlaceholdersReflection,
        builder: Any,
        api: LegendaryMaceApi
    ) {
        val state = api.state
        
        // Wielder Count & Slots
        reflection.addGlobal(builder, "active_wielder_count") { _, _ ->
            Tag.inserting(componentText(state.getActiveWielders().size))
        }

        reflection.addGlobal(builder, "total_mace_count") { _, _ ->
            Tag.inserting(componentText(state.maceCount()))
        }

        reflection.addGlobal(builder, "available_mace_slots") { _, _ ->
            val available = (state.getMaxLegendaryMaces() - state.maceCount()).coerceAtLeast(0)
            Tag.inserting(componentText(available))
        }

        reflection.addGlobal(builder, "max_mace_count") { _, _ ->
            Tag.inserting(componentText(state.getMaxLegendaryMaces()))
        }

        reflection.addGlobal(builder, "has_available_mace_slot") { _, _ ->
            val hasSlot = state.maceCount() < state.getMaxLegendaryMaces()
            Tag.inserting(componentText(boolText(hasSlot)))
        }

        // Wielder Lists
        reflection.addGlobal(builder, "wielder_names") { _, _ ->
            val names = state.getActiveWielders()
                .map { lookupPlayerName(it.playerUuid) }
            Tag.inserting(componentText(formatList(names)))
        }

        reflection.addGlobal(builder, "wielder_uuids") { _, _ ->
            val values = state.getActiveWielders().map { it.playerUuid.toString() }
            Tag.inserting(componentText(formatList(values)))
        }

        // Wielder Timer Lists
        reflection.addGlobal(builder, "wielder_timer_seconds_list") { _, _ ->
            val now = nowSeconds()
            val summary = state.getActiveWielders()
                .map { it to remainingSeconds(it, now) }
                .map { (wielder, seconds) ->
                    "${lookupPlayerName(wielder.playerUuid)}:${formatNumeric(seconds)}"
                }
            Tag.inserting(componentText(formatList(summary)))
        }

        reflection.addGlobal(builder, "wielder_timer_formatted_list") { _, _ ->
            val now = nowSeconds()
            val summary = state.getActiveWielders()
                .map { it to remainingSeconds(it, now) }
                .map { (wielder, seconds) ->
                    "${lookupPlayerName(wielder.playerUuid)}:${formatDuration(seconds)}"
                }
            Tag.inserting(componentText(formatList(summary)))
        }

        // Wielder Kill Info
        reflection.addGlobal(builder, "wielder_last_kill_names") { _, _ ->
            val entries = state.getActiveWielders()
                .map { wielder ->
                    val lastKill = lookupPlayerName(wielder.lastKillUuid)
                    "${lookupPlayerName(wielder.playerUuid)}:$lastKill"
                }
            Tag.inserting(componentText(formatList(entries)))
        }

        // Loose Mace Count
        reflection.addGlobal(builder, "loose_mace_count") { _, _ ->
            Tag.inserting(componentText(state.getLooseMaces().size))
        }

        // Loose Mace Locations
        reflection.addGlobal(builder, "loose_mace_locations") { _, _ ->
            val locations = state.getLooseMaces().map { formatLooseMaceLocation(it) }
            Tag.inserting(componentText(formatList(locations)))
        }

        reflection.addGlobal(builder, "loose_mace_details") { _, _ ->
            val now = nowSeconds()
            val details = state.getLooseMaces().map { formatLooseMaceDetail(it, now) }
            Tag.inserting(componentText(formatList(details)))
        }

        reflection.addGlobal(builder, "loose_mace_world_counts") { _, _ ->
            val counts = state.getLooseMaces()
                .groupBy { it.location.world?.name ?: "unknown" }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .map { (world, entries) -> "$world:${entries.size}" }
            Tag.inserting(componentText(formatList(counts)))
        }

        reflection.addGlobal(builder, "loose_mace_owner_names") { _, _ ->
            val owners = state.getLooseMaces()
                .mapNotNull { it.originalOwnerUuid }
                .map { lookupPlayerName(it) }
            Tag.inserting(componentText(formatList(owners)))
        }

        // Loose Mace Despawn Timers
        reflection.addGlobal(builder, "loose_mace_next_despawn_seconds") { _, _ ->
            val next = nextDespawnSeconds(state.getLooseMaces())
            Tag.inserting(componentText(formatNumeric(next)))
        }

        reflection.addGlobal(builder, "loose_mace_next_despawn_formatted") { _, _ ->
            val next = nextDespawnSeconds(state.getLooseMaces())
            Tag.inserting(componentText(formatDuration(next)))
        }

        reflection.addGlobal(builder, "loose_mace_latest_despawn_seconds") { _, _ ->
            val latest = latestDespawnSeconds(state.getLooseMaces())
            Tag.inserting(componentText(formatNumeric(latest)))
        }

        reflection.addGlobal(builder, "loose_mace_latest_despawn_formatted") { _, _ ->
            val latest = latestDespawnSeconds(state.getLooseMaces())
            Tag.inserting(componentText(formatDuration(latest)))
        }
    }

    /**
     * Registers per-player (audience) placeholders that require player context.
     *
     * These placeholders query player-specific data and are executed for each player viewing the placeholder.
     * The player context is automatically provided by MiniPlaceholders.
     *
     * ## Timer State Values
     *
     * - `active` - Timer > 300 seconds (5+ minutes)
     * - `warning` - Timer 60-300 seconds (1-5 minutes)
     * - `critical` - Timer 0-60 seconds (< 1 minute)
     * - `expired` - Timer ≤ 0 seconds
     * - `inactive` - No active timer or player is not wielding
     *
     * @param reflection The MiniPlaceholders reflection wrapper
     * @param builder The expansion builder
     * @param api The ProjectMace API for accessing mace state
     */
    private fun registerAudiencePlaceholders(
        reflection: MiniPlaceholdersReflection,
        builder: Any,
        api: LegendaryMaceApi
    ) {
        val state = api.state

        // Bloodthirst Timer - Raw Seconds
        reflection.addAudience(builder, "timer_seconds") { player, _, _ ->
            val seconds = state.getRemainingBloodthirstSeconds(player.uniqueId)
            Tag.inserting(componentText(formatNumeric(seconds)))
        }

        // Bloodthirst Timer - Formatted (HH:MM:SS)
        reflection.addAudience(builder, "timer_formatted") { player, _, _ ->
            val seconds = state.getRemainingBloodthirstSeconds(player.uniqueId)
            Tag.inserting(componentText(formatDuration(seconds)))
        }

        // Bloodthirst Timer - Short Form (e.g., "30m", "5h 12m")
        reflection.addAudience(builder, "timer_short") { player, _, _ ->
            val seconds = state.getRemainingBloodthirstSeconds(player.uniqueId)
            Tag.inserting(componentText(formatShortDuration(seconds)))
        }

        // Bloodthirst Timer - In Minutes
        reflection.addAudience(builder, "timer_minutes") { player, _, _ ->
            val seconds = state.getRemainingBloodthirstSeconds(player.uniqueId)
            val minutes = seconds?.div(60.0)?.coerceAtLeast(0.0) ?: 0.0
            Tag.inserting(componentText(formatNumeric(minutes)))
        }

        // Bloodthirst Timer - In Hours
        reflection.addAudience(builder, "timer_hours") { player, _, _ ->
            val seconds = state.getRemainingBloodthirstSeconds(player.uniqueId)
            val hours = seconds?.div(3600.0)?.coerceAtLeast(0.0) ?: 0.0
            Tag.inserting(componentText(formatNumeric(hours)))
        }

        // Bloodthirst Timer - Completion Percentage (0-100)
        reflection.addAudience(builder, "timer_percent") { player, _, _ ->
            val seconds = state.getRemainingBloodthirstSeconds(player.uniqueId)
            val percent = if (seconds != null) {
                (seconds / bloodthirstDurationSeconds()) * 100.0
            } else {
                0.0
            }
            Tag.inserting(componentText("%.2f".format(Locale.US, percent)))
        }

        // Bloodthirst Timer - State (active, warning, critical, expired, inactive)
        reflection.addAudience(builder, "timer_state") { player, _, _ ->
            val seconds = state.getRemainingBloodthirstSeconds(player.uniqueId)
            Tag.inserting(componentText(resolveTimerState(seconds)))
        }

        // Wielder Status
        reflection.addAudience(builder, "is_wielder") { player, _, _ ->
            val isWielder = state.findWielder(player.uniqueId) != null
            Tag.inserting(componentText(boolText(isWielder)))
        }

        reflection.addAudience(builder, "has_timer") { player, _, _ ->
            val hasTimer = state.getRemainingBloodthirstSeconds(player.uniqueId) != null
            Tag.inserting(componentText(boolText(hasTimer)))
        }

        // Timer Warning/Critical States
        reflection.addAudience(builder, "is_timer_warning") { player, _, _ ->
            val seconds = state.getRemainingBloodthirstSeconds(player.uniqueId)
            val warning = seconds != null && seconds > 0 && seconds <= warningThresholdSeconds
            Tag.inserting(componentText(boolText(warning)))
        }

        reflection.addAudience(builder, "is_timer_critical") { player, _, _ ->
            val seconds = state.getRemainingBloodthirstSeconds(player.uniqueId)
            val critical = seconds != null && seconds > 0 && seconds <= criticalThresholdSeconds
            Tag.inserting(componentText(boolText(critical)))
        }

        reflection.addAudience(builder, "is_timer_expired") { player, _, _ ->
            val seconds = state.getRemainingBloodthirstSeconds(player.uniqueId)
            val expired = seconds != null && seconds <= 0
            Tag.inserting(componentText(boolText(expired)))
        }

        // Last Kill Info
        reflection.addAudience(builder, "last_kill_name") { player, _, _ ->
            val wielder = state.findWielder(player.uniqueId)
            Tag.inserting(componentText(lookupPlayerName(wielder?.lastKillUuid)))
        }

        reflection.addAudience(builder, "last_kill_uuid") { player, _, _ ->
            val wielder = state.findWielder(player.uniqueId)
            val value = wielder?.lastKillUuid?.toString() ?: "none"
            Tag.inserting(componentText(value))
        }

        // Mace Identification
        reflection.addAudience(builder, "mace_uuid") { player, _, _ ->
            val wielder = state.findWielder(player.uniqueId)
            val value = wielder?.maceUuid?.toString() ?: "none"
            Tag.inserting(componentText(value))
        }

        // Bloodthirst Elapsed Time - Raw Seconds
        reflection.addAudience(builder, "bloodthirst_elapsed_seconds") { player, _, _ ->
            val remaining = state.getRemainingBloodthirstSeconds(player.uniqueId)
            val elapsed = if (remaining != null) {
                (bloodthirstDurationSeconds() - remaining).coerceAtLeast(0.0)
            } else 0.0
            Tag.inserting(componentText(formatNumeric(elapsed)))
        }

        // Bloodthirst Elapsed Time - Percentage (0-100)
        reflection.addAudience(builder, "bloodthirst_elapsed_percent") { player, _, _ ->
            val remaining = state.getRemainingBloodthirstSeconds(player.uniqueId)
            val elapsedPercent = if (remaining != null) {
                ((bloodthirstDurationSeconds() - remaining) / bloodthirstDurationSeconds()) * 100.0
            } else 0.0
            Tag.inserting(
                componentText("%.2f".format(Locale.US, elapsedPercent.coerceIn(0.0, 100.0)))
            )
        }

        // Mace Hold Duration - Total hold time in readable format (e.g., "14h 32m")
        reflection.addAudience(builder, "mace_hold_duration") { player, _, _ ->
            val wielder = state.findWielder(player.uniqueId)
            val holdMinutes = if (wielder != null) {
                calculateHoldTime()
            } else {
                0L
            }
            Tag.inserting(componentText(formatHoldDuration(holdMinutes)))
        }
    }

    /**
     * Formats a duration in seconds to HH:MM:SS format.
     *
     * @param seconds Duration in seconds, or null for 0
     * @return Formatted time string, e.g., "01:30:45"
     */
    private fun formatDuration(seconds: Double?): String {
        val clamped = (seconds ?: 0.0).coerceAtLeast(0.0)
        val hours = clamped.toLong() / 3600
        val minutes = (clamped.toLong() % 3600) / 60
        val secs = (clamped.toLong() % 60)
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
    }

    /**
     * Formats a duration in seconds to short form.
     *
     * Examples:
     * - 45 seconds -> "45s"
     * - 180 seconds -> "3m 00s"
     * - 3661 seconds -> "1h 01m"
     *
     * @param seconds Duration in seconds, or null for 0
     * @return Formatted time string
     */
    private fun formatShortDuration(seconds: Double?): String {
        val clamped = (seconds ?: 0.0).coerceAtLeast(0.0).toLong()
        val hours = clamped / 3600
        val minutes = (clamped % 3600) / 60
        val secs = clamped % 60
        return when {
            hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
            minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, secs)
            else -> String.format(Locale.US, "%ds", secs)
        }
    }

    /**
     * Formats a mace hold duration in minutes to readable format.
     *
     * Examples:
     * - 30 minutes -> "30m"
     * - 100 minutes -> "1h 40m"
     * - 872 minutes -> "14h 32m"
     *
     * @param minutes Hold time in minutes
     * @return Formatted duration string
     */
    private fun formatHoldDuration(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 -> String.format(Locale.US, "%dh %dm", hours, mins)
            else -> String.format(Locale.US, "%dm", mins)
        }
    }

    /**
     * Calculates total hold time for a wielder in minutes.
     *
     * @return Hold time in minutes (currently returns placeholder value)
     * @see MaceCombatService for actual implementation
     */
    private fun calculateHoldTime(): Long {
        // This is a simplified version - in reality it should read from the data
        // We'll need to access this through the API
        return 0L  // Placeholder - implementation in MaceCombatService
    }

    /**
     * Formats a loose mace's location for display.
     *
     * Format: "world:x,y,z"
     *
     * @param loose The loose mace view
     * @return Formatted location string
     */
    private fun formatLooseMaceLocation(loose: LegendaryLooseMaceView): String {
        val loc = loose.location
        val world = loc.world?.name ?: "unknown"
        return "$world:${loc.blockX},${loc.blockY},${loc.blockZ}"
    }

    /**
     * Formats detailed information about a loose mace.
     *
     * Format: "world:x,y,z (owner=OwnerName, timer=HH:MM:SS)"
     *
     * @param loose The loose mace view
     * @param nowSeconds Current time in seconds since epoch
     * @return Detailed loose mace information
     */
    private fun formatLooseMaceDetail(loose: LegendaryLooseMaceView, nowSeconds: Double): String {
        val loc = loose.location
        val world = loc.world?.name ?: "unknown"
        val owner = loose.originalOwnerUuid?.let { lookupPlayerName(it) } ?: "Unknown"
        val remaining = loose.timerEndEpochSeconds?.let { maxOf(0.0, it - nowSeconds) }
        val timer = formatDuration(remaining)
        return "$world:${loc.blockX},${loc.blockY},${loc.blockZ} (owner=$owner, timer=$timer)"
    }

    /**
     * Finds the next loose mace to despawn (lowest remaining time).
     *
     * @param loose Collection of loose maces
     * @return Seconds until next despawn, or null if no loose maces
     */
    private fun nextDespawnSeconds(loose: Collection<LegendaryLooseMaceView>): Double? {
        val now = nowSeconds()
        return loose.mapNotNull { it.timerEndEpochSeconds?.let { end -> maxOf(0.0, end - now) } }.minOrNull()
    }

    /**
     * Finds the loose mace that will despawn last (highest remaining time).
     *
     * @param loose Collection of loose maces
     * @return Seconds until latest despawn, or null if no loose maces
     */
    private fun latestDespawnSeconds(loose: Collection<LegendaryLooseMaceView>): Double? {
        val now = nowSeconds()
        return loose.mapNotNull { it.timerEndEpochSeconds?.let { end -> maxOf(0.0, end - now) } }.maxOrNull()
    }

    /**
     * Calculates remaining bloodthirst seconds for a wielder.
     *
     * @param wielder The wielder view
     * @param nowSeconds Current time in seconds since epoch
     * @return Remaining seconds, or null if timer has expired
     */
    private fun remainingSeconds(wielder: LegendaryMaceWielderView, nowSeconds: Double): Double? =
        maxOf(0.0, wielder.bloodthirstEndsAtEpochSeconds - nowSeconds)
            .takeIf { wielder.bloodthirstEndsAtEpochSeconds > 0.0 }

    /**
     * Looks up a player's name by UUID.
     *
     * Tries online players first, then offline players, falls back to UUID string.
     *
     * @param uuid The player's UUID, or null
     * @return Player name, "None" if UUID is null
     */
    private fun lookupPlayerName(uuid: UUID?): String {
        if (uuid == null) return "None"
        val online = Bukkit.getPlayer(uuid)
        if (online != null) return online.name
        val offline = Bukkit.getOfflinePlayer(uuid)
        return offline.name ?: uuid.toString()
    }

    /**
     * Formats a collection of strings as comma-separated list.
     *
     * @param values Collection of strings to format
     * @param emptyValue Value to return if collection is empty (default: "None")
     * @return Comma-separated string, or emptyValue if no valid entries
     */
    private fun formatList(values: Collection<String>, emptyValue: String = "None"): String =
        values.filter { it.isNotBlank() }.ifEmpty { listOf(emptyValue) }.joinToString(", ")

    /**
     * Creates a Component from a value.
     *
     * @param value Value to convert to component text
     * @return Adventure Component
     */
    private fun componentText(value: Any): Component = Component.text(value.toString())

    /**
     * Formats a double value as numeric string.
     *
     * @param value The value to format, or null
     * @return Formatted number string with no decimal places
     */
    private fun formatNumeric(value: Double?): String =
        if (value == null) "0" else "%.0f".format(Locale.US, value.coerceAtLeast(0.0))

    /**
     * Converts a boolean to "true" or "false" string.
     *
     * @param value Boolean value
     * @return "true" or "false"
     */
    private fun boolText(value: Boolean): String = if (value) "true" else "false"

    /**
     * Resolves the timer state based on remaining seconds.
     *
     * States:
     * - `inactive` - No timer (null)
     * - `expired` - <= 0 seconds
     * - `critical` - 0-60 seconds
     * - `warning` - 60-300 seconds (1-5 minutes)
     * - `active` - > 300 seconds (5+ minutes)
     *
     * @param seconds Remaining seconds, or null for no timer
     * @return Timer state string
     */
    private fun resolveTimerState(seconds: Double?): String = when {
        seconds == null -> "inactive"
        seconds <= 0.0 -> "expired"
        seconds <= criticalThresholdSeconds -> "critical"
        seconds <= warningThresholdSeconds -> "warning"
        else -> "active"
    }

    /**
     * Gets current time as seconds since epoch.
     *
     * @return Current epoch seconds as Double
     */
    private fun nowSeconds(): Double = Instant.now().epochSecond.toDouble()

    /**
     * Reflection-based wrapper for MiniPlaceholders API.
     *
     * Wraps all MiniPlaceholders API calls via reflection to avoid compile-time dependencies.
     * Uses dynamic proxy objects to implement resolver interfaces without direct type references.
     *
     * ## Benefits
     * - MiniPlaceholders is an optional soft dependency
     * - No runtime errors if plugin is missing
     * - Single reflection overhead per ClassLoader (cached)
     *
     * ## Proxy Pattern
     *
     * Creates dynamic proxy objects that implement interfaces reflectively:
     *
     * ```kotlin
     * Proxy.newProxyInstance(loader, arrayOf(globalResolverClass)) { proxy, method, args ->
     *     when (method.name) {
     *         "tag" -> block(queue, context)  // User's placeholder logic
     *         "toString" -> "ProjectMaceGlobalResolver"
     *         "hashCode" -> System.identityHashCode(proxy)
     *         "equals" -> proxy === args?.getOrNull(0)
     *         else -> null
     *     }
     * }
     * ```
     *
     * @property loader ClassLoader from MiniPlaceholders plugin
     * @property expansionClass Reflection reference to Expansion class
     * @property builderClass Reflection reference to Expansion.Builder class
     * @property globalResolverClass Reflection reference to GlobalTagResolver interface
     * @property audienceResolverClass Reflection reference to AudienceTagResolver interface
     * @property builderMethod Method reference for Expansion.builder(String)
     * @property authorMethod Method reference for Builder.author(String)
     * @property versionMethod Method reference for Builder.version(String)
     * @property globalPlaceholderMethod Method reference for Builder.globalPlaceholder(String, GlobalTagResolver)
     * @property audiencePlaceholderMethod Method reference for Builder.audiencePlaceholder(Class, String, AudienceTagResolver)
     * @property buildMethod Method reference for Builder.build()
     * @property registerMethod Method reference for Expansion.register()
     * @property unregisterMethod Method reference for Expansion.unregister()
     * @property registeredMethod Method reference for Expansion.registered()
     *
     * @see MiniPlaceholderBridge
     */
    private data class MiniPlaceholdersReflection(
        val loader: ClassLoader,
        private val expansionClass: Class<*>,
        private val builderClass: Class<*>,
        private val globalResolverClass: Class<*>,
        private val audienceResolverClass: Class<*>,
        private val builderMethod: Method,
        private val authorMethod: Method,
        private val versionMethod: Method,
        private val globalPlaceholderMethod: Method,
        private val audiencePlaceholderMethod: Method,
        private val buildMethod: Method,
        private val registerMethod: Method,
        private val unregisterMethod: Method,
        private val registeredMethod: Method
    ) {
        /**
         * Creates a new expansion builder.
         *
         * @param name Expansion name (e.g., "projectmace")
         * @return Expansion.Builder instance
         */
        fun newBuilder(name: String): Any = builderMethod.invoke(null, name)

        /**
         * Sets the author(s) of the expansion.
         *
         * @param builder Expansion builder
         * @param author Author names, comma-separated
         */
        fun setAuthor(builder: Any, author: String) {
            authorMethod.invoke(builder, author)
        }

        /**
         * Sets the version of the expansion.
         *
         * @param builder Expansion builder
         * @param version Version string, or null
         */
        fun setVersion(builder: Any, version: String?) {
            versionMethod.invoke(builder, version)
        }

        /**
         * Adds a global placeholder resolver.
         *
         * Global placeholders don't require player context.
         *
         * @param builder Expansion builder
         * @param name Placeholder name (without prefix/suffix, e.g., "active_wielder_count")
         * @param block Lambda that resolves the placeholder
         */
        fun addGlobal(builder: Any, name: String, block: (ArgumentQueue, Context) -> Tag) {
            globalPlaceholderMethod.invoke(builder, name, createGlobalResolver(block))
        }

        /**
         * Adds an audience (per-player) placeholder resolver.
         *
         * Audience placeholders require player context.
         *
         * @param builder Expansion builder
         * @param name Placeholder name (without prefix/suffix, e.g., "timer_seconds")
         * @param block Lambda that resolves the placeholder for a specific player
         */
        fun addAudience(builder: Any, name: String, block: (Player, ArgumentQueue, Context) -> Tag) {
            audiencePlaceholderMethod.invoke(
                builder,
                Player::class.java,
                name,
                createAudienceResolver(block)
            )
        }

        /**
         * Builds the expansion from the builder.
         *
         * @param builder Expansion builder
         * @return Built Expansion instance
         */
        fun build(builder: Any): Any = buildMethod.invoke(builder)

        /**
         * Registers the expansion with MiniPlaceholders.
         *
         * @param expansion Built expansion instance
         */
        fun register(expansion: Any) {
            registerMethod.invoke(expansion)
        }

        /**
         * Unregisters a previously registered expansion if applicable.
         *
         * @param expansion Expansion instance to unregister, or null to skip
         */
        fun unregisterIfRegistered(expansion: Any?) {
            if (expansion == null) return
            val registered = registeredMethod.invoke(expansion) as? Boolean ?: false
            if (registered) {
                unregisterMethod.invoke(expansion)
            }
        }

        /**
         * Creates a dynamic proxy object implementing GlobalTagResolver.
         *
         * Uses [Proxy.newProxyInstance] to avoid compile-time dependency on the interface.
         * The proxy forwards method calls to the provided block.
         *
         * @param block Lambda that handles the "tag" method
         * @return Dynamic proxy instance implementing GlobalTagResolver
         */
        private fun createGlobalResolver(block: (ArgumentQueue, Context) -> Tag): Any =
            Proxy.newProxyInstance(loader, arrayOf(globalResolverClass)) { proxy, method, args ->
                when (method.name) {
                    "tag" -> {
                        val queue = args?.getOrNull(0) as? ArgumentQueue
                        val context = args?.getOrNull(1) as? Context
                        if (queue == null || context == null) {
                            Tag.inserting(Component.empty())
                        } else {
                            block(queue, context)
                        }
                    }

                    "toString" -> "ProjectMaceGlobalResolver"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    else -> null
                }
            }

        /**
         * Creates a dynamic proxy object implementing AudienceTagResolver.
         *
         * Uses [Proxy.newProxyInstance] to avoid compile-time dependency on the interface.
         * The proxy forwards method calls to the provided block with player context.
         *
         * @param block Lambda that handles the "tag" method with player, queue, and context
         * @return Dynamic proxy instance implementing AudienceTagResolver
         */
        private fun createAudienceResolver(block: (Player, ArgumentQueue, Context) -> Tag): Any =
            Proxy.newProxyInstance(loader, arrayOf(audienceResolverClass)) { proxy, method, args ->
                when (method.name) {
                    "tag" -> {
                        val audience = args?.getOrNull(0) as? Player
                        val queue = args?.getOrNull(1) as? ArgumentQueue
                        val context = args?.getOrNull(2) as? Context
                        if (audience == null || queue == null || context == null) {
                            Tag.inserting(Component.empty())
                        } else {
                            block(audience, queue, context)
                        }
                    }

                    "toString" -> "ProjectMaceAudienceResolver"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    else -> null
                }
            }

        /**
         * Companion object for reflection caching and initialization.
         */
        companion object {
            private val cache = ConcurrentHashMap<ClassLoader, MiniPlaceholdersReflection>()

            /**
             * Gets or creates reflection wrapper for a ClassLoader.
             *
             * Uses caching to avoid repeated reflection overhead.
             *
             * @param loader ClassLoader to get reflection for (usually from MiniPlaceholders plugin)
             * @return Cached MiniPlaceholdersReflection instance
             */
            fun forLoader(loader: ClassLoader): MiniPlaceholdersReflection =
                cache.computeIfAbsent(loader) { buildReflection(it) }

            /**
             * Builds reflection wrapper by loading classes and methods from ClassLoader.
             *
             * This method performs the expensive reflection operations:
             * - Loads MiniPlaceholders API classes
             * - Resolves method references
             * - Stores them for reuse
             *
             * @param loader ClassLoader to load classes from
             * @return New MiniPlaceholdersReflection instance
             * @throws ClassNotFoundException If MiniPlaceholders classes not found
             * @throws NoSuchMethodException If expected methods not found
             */
            private fun buildReflection(loader: ClassLoader): MiniPlaceholdersReflection {
                val expansionClass = Class.forName("io.github.miniplaceholders.api.Expansion", true, loader)
                val builderClass = Class.forName("io.github.miniplaceholders.api.Expansion\$Builder", true, loader)
                val globalResolverClass =
                    Class.forName("io.github.miniplaceholders.api.resolver.GlobalTagResolver", true, loader)
                val audienceResolverClass =
                    Class.forName("io.github.miniplaceholders.api.resolver.AudienceTagResolver", true, loader)

                val builderMethod = expansionClass.getMethod("builder", String::class.java)
                val authorMethod = builderClass.getMethod("author", String::class.java)
                val versionMethod = builderClass.getMethod("version", String::class.java)
                val globalPlaceholderMethod =
                    builderClass.getMethod("globalPlaceholder", String::class.java, globalResolverClass)
                val audiencePlaceholderMethod = builderClass.getMethod(
                    "audiencePlaceholder",
                    Class::class.java,
                    String::class.java,
                    audienceResolverClass
                )
                val buildMethod = builderClass.getMethod("build")
                val registerMethod = expansionClass.getMethod("register")
                val unregisterMethod = expansionClass.getMethod("unregister")
                val registeredMethod = expansionClass.getMethod("registered")

                return MiniPlaceholdersReflection(
                    loader = loader,
                    expansionClass = expansionClass,
                    builderClass = builderClass,
                    globalResolverClass = globalResolverClass,
                    audienceResolverClass = audienceResolverClass,
                    builderMethod = builderMethod,
                    authorMethod = authorMethod,
                    versionMethod = versionMethod,
                    globalPlaceholderMethod = globalPlaceholderMethod,
                    audiencePlaceholderMethod = audiencePlaceholderMethod,
                    buildMethod = buildMethod,
                    registerMethod = registerMethod,
                    unregisterMethod = unregisterMethod,
                    registeredMethod = registeredMethod
                )
            }
        }
    }

    /**
     * Companion object containing constants for the placeholder bridge.
     */
    companion object {
        /**
         * The expansion name registered with both PlaceholderAPI and MiniPlaceholders.
         *
         * This is the namespace for all ProjectMace placeholders.
         * - PlaceholderAPI format: `%projectmace_placeholder_name%`
         * - MiniPlaceholders format: `<projectmace_placeholder_name>`
         */
        private const val EXPANSION_NAME = "projectmace"
    }
}

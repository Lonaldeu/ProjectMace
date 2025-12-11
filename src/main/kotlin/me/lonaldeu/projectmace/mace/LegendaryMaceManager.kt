package me.lonaldeu.projectmace.mace

import me.lonaldeu.projectmace.ProjectMacePlugin
import me.lonaldeu.projectmace.mace.admin.AdminRefund
import me.lonaldeu.projectmace.mace.admin.AdminTimers
import me.lonaldeu.projectmace.mace.admin.AdminTransfer
import me.lonaldeu.projectmace.mace.admin.AdminUnclaim
import me.lonaldeu.projectmace.mace.api.LegendaryMaceApi
import me.lonaldeu.projectmace.mace.api.LegendaryMaceApiProvider
import me.lonaldeu.projectmace.mace.api.LegendaryMaceCombatApi
import me.lonaldeu.projectmace.mace.api.LegendaryMaceControl
import me.lonaldeu.projectmace.mace.api.LegendaryMaceItemApi
import me.lonaldeu.projectmace.mace.api.LegendaryMaceStateView
import me.lonaldeu.projectmace.mace.api.impl.CombatApiImpl
import me.lonaldeu.projectmace.mace.api.impl.ControlImpl
import me.lonaldeu.projectmace.mace.api.impl.ItemApiImpl
import me.lonaldeu.projectmace.mace.api.impl.LegendaryMaceManagerApiImpl
import me.lonaldeu.projectmace.mace.api.impl.StateViewImpl
import me.lonaldeu.projectmace.mace.core.LegendaryMaceAnnouncements
import me.lonaldeu.projectmace.mace.core.LegendaryMaceLogger
import me.lonaldeu.projectmace.mace.core.MaceChunkControl
import me.lonaldeu.projectmace.mace.core.MaceItems
import me.lonaldeu.projectmace.mace.core.MaceKeys
import me.lonaldeu.projectmace.mace.core.MaceMessaging
import me.lonaldeu.projectmace.mace.core.MaceRegistrations
import me.lonaldeu.projectmace.mace.core.MacePlaceholderBridge
import me.lonaldeu.projectmace.mace.core.clamp01
import me.lonaldeu.projectmace.mace.core.nowSeconds
import me.lonaldeu.projectmace.mace.command.MaceCommandService
import me.lonaldeu.projectmace.mace.domain.MaceCombatService
import me.lonaldeu.projectmace.mace.domain.MaceEffects
import me.lonaldeu.projectmace.mace.domain.MaceInventoryGuard
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import me.lonaldeu.projectmace.mace.domain.MaceSearchBridge
import me.lonaldeu.projectmace.mace.domain.MaceState
import me.lonaldeu.projectmace.mace.events.CraftEvents
import me.lonaldeu.projectmace.mace.events.DeathAndDropEvents
import me.lonaldeu.projectmace.mace.events.PlayerLifecycleEvents
import me.lonaldeu.projectmace.mace.events.WorldItemEvents
import me.lonaldeu.projectmace.mace.persistence.LegendaryMacePersistence
import me.lonaldeu.projectmace.mace.persistence.MacePersistence
import me.lonaldeu.projectmace.mace.persistence.PersistenceBridge
import me.lonaldeu.projectmace.mace.persistence.PersistenceFactory
import me.lonaldeu.projectmace.mace.domain.MaceSearchService
import me.lonaldeu.projectmace.mace.domain.model.LooseMace
import me.lonaldeu.projectmace.mace.domain.model.MaceConstants
import me.lonaldeu.projectmace.mace.tasks.MaceBackgroundTasks
import me.lonaldeu.projectmace.mace.tasks.MaceDespawnTasks
import me.lonaldeu.projectmace.platform.PlatformScheduler
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ItemDespawnEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack

/**
 * Kotlin translation of the legacy PySpigot "Legendary Mace" script.
 * This service owns all game logic, persistence, and event handling.
 */
class LegendaryMaceManager(
    private val registry: me.lonaldeu.projectmace.mace.core.MaceServiceRegistry
) {
    private val plugin = registry.plugin
    private val scheduler = registry.scheduler
    private val messageService = registry.messages

    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
    private val announcements = LegendaryMaceAnnouncements(messageService)
    private val placeholderBridge = MacePlaceholderBridge(
        plugin = plugin,
        maceManager = this,
        bloodthirstDurationSeconds = ::bloodthirstDurationSeconds,
        warningThresholdSeconds = plugin.configService.getPlaceholderWarningThresholdSeconds(),
        criticalThresholdSeconds = plugin.configService.getPlaceholderCriticalThresholdSeconds()
    )
    private val commandService = MaceCommandService(plugin, this, ::reloadConfig)

    private val state = MaceState()
    private val keys = MaceKeys(plugin)
    private val items = MaceItems(keys, plugin.configService.getCraftingDurability())
    private val messaging = MaceMessaging(
        scheduler = scheduler,
        legacySerializer = legacySerializer,
        miniMessage = miniMessage,
        announcements = announcements,
        announcementCooldownMillis = plugin.configService.getAnnouncementCooldownMillis(),
        lastAnnouncementTimes = state.lastAnnouncementTime
    )
    private val chunkControl = MaceChunkControl(
        plugin = plugin,
        scheduler = scheduler,
        enableChunkForceLoad = plugin.configService.isChunkForceLoadEnabled(),
        chunkUnloadDelayTicks = plugin.configService.getChunkUnloadDelayTicks()
    )
    private val effects = MaceEffects(
        state = state,
        scheduler = scheduler,
        bloodthirstDurationSeconds = ::bloodthirstDurationSeconds,
        particleIntervalTicks = plugin.configService.getParticleIntervalTicks(),
        particleCount = plugin.configService.getParticleCount(),
        soundVolume = plugin.configService.getSoundVolume().toFloat(),
        soundPitchMin = plugin.configService.getSoundPitchMin().toFloat(),
        soundPitchMax = plugin.configService.getSoundPitchMax().toFloat(),
        soundHeartbeatThresholdSeconds = plugin.configService.getSoundHeartbeatThresholdSeconds(),
        nowSeconds = ::nowSeconds
    )
    private val eventLogger = LegendaryMaceLogger(plugin)
    private val lifecycle = MaceLifecycle(
        state = state,
        items = items,
        chunkControl = chunkControl,
        messaging = messaging,
        announcements = announcements,
        scheduler = scheduler,
        breakupVoicelines = plugin.voicelineService.getBreakupLines(),
        stopWielderEffects = effects::stopWielderEffects,
        saveData = ::saveData,
        messageService = messageService,
        logVerboseEvent = ::logVerboseEvent,
        currentTime = ::nowSeconds
    )
    private val inventoryGuard = MaceInventoryGuard(
        plugin = plugin,
        isRealMace = lifecycle::isRealMace,
        messageService = messageService,
        blockDecoratedPots = plugin.configService.isInventoryGuardBlockDecoratedPots(),
        blockItemFrames = plugin.configService.isInventoryGuardBlockItemFrames(),
        blockArmorStands = plugin.configService.isInventoryGuardBlockArmorStands(),
        blockAllays = plugin.configService.isInventoryGuardBlockAllays()
    )
    private val despawnTasks = MaceDespawnTasks(
        scheduler = scheduler,
        state = state,
        chunkControl = chunkControl,
        lifecycle = lifecycle,
        messaging = messaging,
        announcements = announcements,
        looseMaceDespawnDelayTicks = plugin.configService.getLooseMaceDespawnDelayTicks()
    )
    private val persistence: MacePersistence = PersistenceFactory(plugin).create(
        storageType = plugin.configService.getStorageType(),
        maceWielders = state.maceWielders,
        looseMaces = state.looseMaces,
        pendingMaceRemoval = state.pendingMaceRemoval,
        startWielderEffects = effects::startWielderEffects
    )
    private val persistenceBridge = PersistenceBridge(persistence, state, scheduler, scheduler.isFolia(), despawnTasks)
    private val registrations = MaceRegistrations(plugin, commandService, placeholderBridge)
    private val searchBridge = MaceSearchBridge(
        scheduler = scheduler,
        state = state,
        isFolia = scheduler.isFolia(),
        maxLegendaryMaces = ::maxLegendaryMaces,
        lifecycle = lifecycle,
        messaging = messaging,
        logVerboseEvent = ::logVerboseEvent
    )
    private val searchService: MaceSearchService = searchBridge.searchService

    private val backgroundTasks = MaceBackgroundTasks(
        scheduler = scheduler,
        state = state,
        lifecycle = lifecycle,
        stopMaceDespawnSequence = despawnTasks::stopMaceDespawnSequence,
        messaging = messaging,
        flushDataIfDirty = ::flushDataIfDirty,
        bloodthirstDurationSeconds = ::bloodthirstDurationSeconds,
        lastChanceThresholdSeconds = plugin.configService.getLastChanceThresholdSeconds(),
        idleWhisperIntervalTicks = plugin.configService.getIdleWhisperIntervalTicks(),
        warningThresholdPercentage = plugin.configService.getWarningThresholdPercentage(),
        nowSeconds = ::nowSeconds,
        idleVoicelines = plugin.voicelineService.getIdleLines(),
        nearingExpiryVoicelines = plugin.voicelineService.getNearingExpiryLines(),
        lastChanceVoicelines = plugin.voicelineService.getLastChanceLines()
    )

    private val combatSettings = loadCombatSettings()

    private val combatService = MaceCombatService(
        plugin = plugin,
        state = state,
        messaging = messaging,
        messageService = messageService,
        logVerboseEvent = ::logVerboseEvent,
        bloodthirstDurationSeconds = ::bloodthirstDurationSeconds,
        nowSeconds = ::nowSeconds,
        combatVoicelines = plugin.voicelineService.getCombatLines(),
        settings = combatSettings
    )
    
    private val maceContext = me.lonaldeu.projectmace.mace.core.MaceContext(
        registry = registry,
        state = state,
        items = items,
        effects = effects,
        messaging = messaging,
        lifecycle = lifecycle,
        combat = combatService,
        chunkControl = chunkControl,
        despawnTasks = despawnTasks,
        eventLogger = eventLogger,
        saveData = ::saveData,
        nowSeconds = ::nowSeconds
    )

    private val api: LegendaryMaceApi = LegendaryMaceManagerApiImpl(
        state = StateViewImpl(state, lifecycle, items, ::maxLegendaryMaces, ::nowSeconds),
        control = ControlImpl(
            state,
            lifecycle,
            effects,
            items,
            chunkControl,
            despawnTasks,
            ::saveData,
            ::nowSeconds
        ),
        items = ItemApiImpl(items, lifecycle),
        combat = CombatApiImpl(state, combatService)
    )

    private val adminUnclaim = AdminUnclaim(
        state = state,
        effects = effects,
        lifecycle = lifecycle,
        items = items,
        messaging = messaging,
        despawnTasks = despawnTasks,
        saveData = ::saveData,
        flushDataIfDirty = ::flushDataIfDirty,
        logVerboseEvent = ::logVerboseEvent,
        resolveOfflinePlayer = ::resolveOfflinePlayer
    )

    private val adminTransfer = AdminTransfer(
        state = state,
        lifecycle = lifecycle,
        effects = effects,
        items = items,
        messaging = messaging,
        saveData = ::saveData,
        flushDataIfDirty = ::flushDataIfDirtyDefault,
        logVerboseEvent = ::logVerboseEvent,
        resolveOfflinePlayer = ::resolveOfflinePlayer
    )

    private val adminRefund = AdminRefund(
        state = state,
        lifecycle = lifecycle,
        effects = effects,
        items = items,
        messaging = messaging,
        saveData = ::saveData,
        flushDataIfDirty = ::flushDataIfDirtyDefault,
        logVerboseEvent = ::logVerboseEvent,
        maxLegendaryMaces = ::maxLegendaryMaces,
        bloodthirstDurationSeconds = ::bloodthirstDurationSeconds,
        nowSeconds = ::nowSeconds
    )

    private val adminTimers = AdminTimers(
        state = state,
        lifecycle = lifecycle,
        messaging = messaging,
        saveData = ::saveData,
        flushDataIfDirty = ::flushDataIfDirtyDefault,
        resolveOfflinePlayer = ::resolveOfflinePlayer,
        resolveDisplayName = ::resolveDisplayName,
        formatDuration = ::formatDuration,
        maxLegendaryMaces = ::maxLegendaryMaces,
        bloodthirstDurationSeconds = ::bloodthirstDurationSeconds,
        nowSeconds = ::nowSeconds
    )

    private val craftEvents = CraftEvents(maceContext)

    private val playerLifecycleEvents = PlayerLifecycleEvents(maceContext)

    private val deathAndDropEvents = DeathAndDropEvents(maceContext)

    private val worldItemEvents = WorldItemEvents(maceContext)

    private val durabilityEvents = me.lonaldeu.projectmace.mace.events.MaceDurabilityEvents(
        items = items,
        onMaceBreak = ::handleMaceBreak
    )

    private val enchantEvents = me.lonaldeu.projectmace.mace.events.MaceEnchantEvents(
        items = items,
        config = plugin.configService
    )

    internal fun getWielderUuids(): Set<UUID> = state.maceWielders.keys.toSet()

    fun getApi(): LegendaryMaceApi = api

    fun enable() {
        val loadedLooseMaces = persistence.loadData()
        LegendaryMaceApiProvider.register(api)
        if (isInventoryGuardEnabled()) {
            inventoryGuard.register()
        } else {
            plugin.logger.info("[Mace] Inventory guard disabled via config")
        }
        combatService.register()
        registrations.registerListeners(
            craftEvents,
            playerLifecycleEvents,
            deathAndDropEvents,
            worldItemEvents,
            durabilityEvents,
            enchantEvents
        )
        registrations.registerCommands()
        if (isPlaceholderExpansionEnabled()) {
            registrations.registerPlaceholders()
        } else {
            plugin.logger.info("[Mace] MiniPlaceholders integration disabled via config")
        }

        if (isBackgroundTasksEnabled()) {
            backgroundTasks.start()
        } else {
            plugin.logger.info("[Mace] Background tasks disabled via config; timers may not persist")
        }
        persistenceBridge.resumeLooseMaces(loadedLooseMaces)
    }

    fun disable() {
        LegendaryMaceApiProvider.unregister(api)
        backgroundTasks.stop()

        state.maceWielders.values.forEach { wielder ->
            wielder.particleTask?.cancel()
            wielder.soundTask?.cancel()
            wielder.particleTask = null
            wielder.soundTask = null
        }

        state.looseMaces.values.forEach { loose ->
            loose.broadcastTask?.cancel()
            loose.broadcastTask = null
            loose.despawnTask?.cancel()
            loose.despawnTask = null
            chunkControl.setChunkForceState(loose.location, false)
        }

        // Use synchronous flush during disable - async scheduling not allowed when plugin is disabling
        persistenceBridge.flushDataSync(true)
        
        // Close persistence connection and release resources
        persistence.close()
    }

    private fun reloadConfig() {
        plugin.configService.reloadConfig()
        plugin.messageService.loadMessages()
        plugin.voicelineService.load()
        plugin.logger.info("[Mace] Configuration reloaded")
    }

    private fun saveData() {
        persistenceBridge.saveData()
    }

    private fun flushDataIfDirty(force: Boolean = false) {
        persistenceBridge.flushDataIfDirty(force)
    }

    private fun flushDataIfDirtyDefault() {
        flushDataIfDirty(false)
    }

    private fun handleMaceBreak(player: Player, maceUuid: UUID) {
        // Remove from wielder state
        state.maceWielders.remove(player.uniqueId)
        
        // Remove the broken mace from inventory
        items.removeTaggedMaces(player) { item -> 
            items.getMaceUuid(item) == maceUuid 
        }
        
        // Announce the breaking
        player.sendMessage(messageService.getLegacy("mace.broken"))
        messaging.broadcast(messageService.getLegacy("mace.broken-broadcast", "player" to player.name))
        
        // Log event
        logVerboseEvent(
            eventType = "MACE_BROKEN",
            playerName = player.name,
            playerUuid = player.uniqueId,
            maceUuid = maceUuid,
            location = player.location,
            outcome = "destroyed",
            reason = "durability_depleted"
        )
        
        saveData()
    }

    private fun logVerboseEvent(
        eventType: String,
        playerName: String? = null,
        playerUuid: UUID? = null,
        maceUuid: UUID? = null,
        location: Location? = null,
        containerContext: String? = null,
        outcome: String? = null,
        reason: String? = null,
        additionalContext: Map<String, Any?> = emptyMap(),
        timerEnd: Double? = null,
        timeLeft: Double? = null
    ) {
        eventLogger.logVerboseEvent(
            eventType = eventType,
            playerName = playerName,
            playerUuid = playerUuid,
            maceUuid = maceUuid,
            location = location,
            containerContext = containerContext,
            outcome = outcome,
            reason = reason,
            additionalContext = additionalContext,
            timerEnd = timerEnd,
            timeLeft = timeLeft
        )
    }

    // region Utility helpers

    private fun isCraftingEnabled(): Boolean = plugin.configService.get("crafting.enabled", true)

    private fun isInventoryGuardEnabled(): Boolean = plugin.configService.get("features.inventory-guard.enabled", true)

    private fun isPlaceholderExpansionEnabled(): Boolean = plugin.configService.get("features.placeholders.enabled", true)

    private fun isBackgroundTasksEnabled(): Boolean = plugin.configService.get("features.background-tasks.enabled", true)

    private fun maxLegendaryMaces(): Int = plugin.configService.get(
        "max-legendary-maces",
        MaceConstants.DEFAULT_MAX_LEGENDARY_MACES
    ).coerceAtLeast(1)

    private fun maxMacesPerPlayer(): Int = plugin.configService.getMaxMacesPerPlayer()

    private fun bloodthirstDurationSeconds(): Long {
        val hours = plugin.configService.get("bloodthirst-duration-hours", 24).coerceAtLeast(0)
        return if (hours == 0) Long.MAX_VALUE else hours.toLong() * 60L * 60L
    }

    private fun loadCombatSettings(): MaceCombatService.CombatSettings {
        val bloodthirstHours = plugin.configService.get("bloodthirst-duration-hours", 24)
        val combatEnabled = if (bloodthirstHours == 0) false else plugin.configService.get("features.combat.enabled", true)
        
        return MaceCombatService.CombatSettings(
        enabled = combatEnabled,
        trackDamage = plugin.configService.get("features.combat.track-damage", true),
        awardKills = plugin.configService.get("features.combat.award-worthy-kills", true),
        sendMessages = plugin.configService.get("features.combat.send-messages", true),
        sendVoicelines = plugin.configService.get("features.combat.send-voicelines", true),
        baseDamage = plugin.configService.getCombatBaseDamage(),
        damageMultiplier = plugin.configService.getCombatDamageMultiplier(),
        healthMultiplier = plugin.configService.getCombatHealthMultiplier(),
        armorMultiplier = plugin.configService.getCombatArmorMultiplier(),
        totemBonus = plugin.configService.getCombatTotemBonus(),
        worthyThreshold = plugin.configService.getCombatWorthyThreshold(),
        easyThreshold = plugin.configService.getCombatEasyThreshold(),
        holdTimeBaseDamageRequirement = plugin.configService.getHoldTimeBaseDamageRequirement(),
        holdTimeEscalationRate = plugin.configService.getHoldTimeEscalationRate()
    )
    }

    private fun craftCooldownSeconds(): Double = plugin.configService
        .get("crafting.cooldown-seconds", 3.0)
        .coerceAtLeast(0.0)

    // endregion

    // region Admin command helpers

    internal fun handleUnclaimCommand(sender: CommandSender, args: List<String>): Boolean =
        adminUnclaim.handle(sender, args)

    internal fun handleTransferCommand(sender: CommandSender, currentOwnerName: String?, newOwnerName: String?): Boolean =
        adminTransfer.handle(sender, currentOwnerName, newOwnerName)

    internal fun handleRefundCommand(sender: CommandSender, targetName: String?): Boolean =
        adminRefund.handle(sender, targetName)

    internal fun handleSearchCommand(sender: CommandSender): Boolean {
        searchService.handleSearchCommand(sender)
        return true
    }

    internal fun handleTimerCommand(sender: CommandSender, args: List<String>): Boolean =
        adminTimers.handleTimerCommand(sender, args)

    internal fun handleBloodTimerCommand(sender: CommandSender, args: List<String>): Boolean =
        adminTimers.handleBloodTimerCommand(sender, args)

    private fun resolveOfflinePlayer(name: String): OfflinePlayer? {
        Bukkit.getPlayerExact(name)?.let { return it }
        val offline = Bukkit.getOfflinePlayer(name)
        return if (offline.hasPlayedBefore() || offline.isOnline) offline else null
    }

    private fun resolveDisplayName(player: OfflinePlayer): String = player.name ?: player.uniqueId.toString()

    private fun formatDuration(seconds: Long): String {
        val clamped = max(0L, seconds)
        val hours = clamped / 3600
        val minutes = (clamped % 3600) / 60
        val secs = clamped % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    private fun nowSeconds(): Double = Instant.now().epochSecond.toDouble()

    // endregion
}

package me.lonaldeu.projectmace.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.io.IOException

/**
 * Configuration service using Bukkit's native YamlConfiguration
 * Provides structured YAML configuration management for the plugin
 * 
 * Thread-safe for reading; modifications should be done on main thread or synchronized
 */
class ConfigService(private val plugin: Plugin) {
    
    @PublishedApi
    internal var config: FileConfiguration = plugin.config
    private val dataFolder: File = plugin.dataFolder
    
    /**
     * Typed configuration object for clean API access.
     * Populated from YAML on load/reload.
     */
    lateinit var typedConfig: MaceConfig
        private set
    
    /**
     * Load or create default configuration
     */
    fun loadConfig() {
        try {
            // Ensure data folder exists
            if (!dataFolder.exists()) {
                dataFolder.mkdirs()
            }
            
            // Save default config if it doesn't exist
            val configFile = File(dataFolder, "config.yml")
            if (!configFile.exists()) {
                plugin.saveDefaultConfig()
                plugin.logger.info("Created default config.yml")
            }
            
            // Reload config from disk
            plugin.reloadConfig()
            config = plugin.config
            
            // Set defaults
            setDefaults()
            
            // Typed config is now populated explicitly after license validation
            // populateTypedConfig()
            
            plugin.logger.info("Configuration loaded successfully")
            
        } catch (e: Exception) {
            plugin.logger.severe("Failed to load configuration: ${e.message}")
            throw e
        }
    }
    
    /**
     * Set default configuration values
     */
    private fun setDefaults() {
        config.addDefault("license-key", "YOUR-LICENSE-KEY")
        
        // General
        config.addDefault("general.debug", false)
        config.addDefault("general.storage-type", "sqlite")
        config.addDefault("general.autosave-interval", 300)
        
        // Gameplay
        config.addDefault("gameplay.max-maces", 3)
        config.addDefault("gameplay.bloodthirst-hours", 24)
        
        config.addDefault("gameplay.crafting.enabled", true)
        config.addDefault("gameplay.crafting.cooldown-seconds", 3.0)
        config.addDefault("gameplay.crafting.durability", 500)
        config.addDefault("gameplay.crafting.max-per-player", 1)
        
        config.addDefault("gameplay.enchanting.anvil", true)
        config.addDefault("gameplay.enchanting.table", true)
        config.addDefault("gameplay.enchanting.allowed", listOf(
            "unbreaking", "mending", "sharpness", "smite", "bane_of_arthropods",
            "fire_aspect", "looting", "knockback", "sweeping_edge",
            "density", "breach", "wind_burst", "curse_of_vanishing"
        ))
        config.addDefault("gameplay.enchanting.blocked", emptyList<String>())
        
        config.addDefault("gameplay.combat.enabled", true)
        config.addDefault("gameplay.combat.track-damage", true)
        config.addDefault("gameplay.combat.award-worthy-kills", true)
        
        config.addDefault("gameplay.combat.scoring.base-damage", 5.0)
        config.addDefault("gameplay.combat.scoring.damage-multiplier", 1.0)
        config.addDefault("gameplay.combat.scoring.health-multiplier", 0.5)
        config.addDefault("gameplay.combat.scoring.armor-multiplier", 2.0)
        config.addDefault("gameplay.combat.scoring.totem-bonus", 20.0)
        config.addDefault("gameplay.combat.scoring.worthy-kill-threshold", 10.0)
        config.addDefault("gameplay.combat.scoring.easy-kill-threshold", 5.0)
        
        config.addDefault("gameplay.inventory-guard.enabled", true)
        config.addDefault("gameplay.inventory-guard.block-decorated-pots", true)
        config.addDefault("gameplay.inventory-guard.block-item-frames", true)
        config.addDefault("gameplay.inventory-guard.block-armor-stands", true)
        config.addDefault("gameplay.inventory-guard.block-allays", true)
        config.addDefault("gameplay.inventory-guard.block-drop-on-death", false)
        
        config.addDefault("gameplay.loose-mace.despawn-delay-seconds", 600)
        config.addDefault("gameplay.loose-mace.void-recovery-enabled", true)
        
        config.addDefault("gameplay.hold-time.tracking-enabled", true)
        config.addDefault("gameplay.hold-time.base-damage-requirement", 10.0)
        config.addDefault("gameplay.hold-time.escalation-rate", 0.5)
        
        // Visuals
        config.addDefault("visuals.effects.particles.enabled", true)
        config.addDefault("visuals.effects.particles.interval-seconds", 1)
        config.addDefault("visuals.effects.particles.count", 10)
        
        config.addDefault("visuals.effects.sounds.enabled", true)
        config.addDefault("visuals.effects.sounds.heartbeat-enabled", true)
        config.addDefault("visuals.effects.sounds.heartbeat-threshold", 60)
        config.addDefault("visuals.effects.sounds.volume", 0.5)
        config.addDefault("visuals.effects.sounds.pitch-min", 0.5)
        config.addDefault("visuals.effects.sounds.pitch-max", 1.5)
        
        config.addDefault("visuals.announcements.cooldown-milliseconds", 2000)
        config.addDefault("visuals.announcements.mace-lost-enabled", true)
        config.addDefault("visuals.announcements.bloodthirst-unmet-enabled", true)
        config.addDefault("visuals.announcements.divine-intervention-enabled", true)
        config.addDefault("visuals.announcements.combat-messages-enabled", true)
        config.addDefault("visuals.announcements.combat-voicelines-enabled", true)
        
        config.addDefault("visuals.placeholders.enabled", true)
        config.addDefault("visuals.placeholders.format.empty-value", "None")
        config.addDefault("visuals.placeholders.format.boolean-true", "true")
        config.addDefault("visuals.placeholders.format.boolean-false", "false")
        config.addDefault("visuals.placeholders.format.list-separator", ", ")
        
        // System
        config.addDefault("system.timers.warning-threshold", 300)
        config.addDefault("system.timers.critical-threshold", 60)
        config.addDefault("system.timers.last-chance-threshold", 60)
        config.addDefault("system.timers.idle-whisper-interval", 150)
        config.addDefault("system.timers.status-check-interval", 10)
        
        config.addDefault("system.performance.combat-log-max-history", 1000)
        config.addDefault("system.performance.combat-log-pruning-interval", 60)
        config.addDefault("system.performance.chunk-force-load", true)
        config.addDefault("system.performance.chunk-unload-delay", 5)
        
        config.addDefault("system.logging.verbose-events", false)
        config.addDefault("system.logging.log-directory", "mace_logs")
        config.addDefault("system.logging.include-location-data", true)
        config.addDefault("system.logging.include-timer-data", true)
        
        config.options().copyDefaults(true)
        saveConfig()
    }
    
    /**
     * Populate the typed configuration object from YAML values.
     * This creates a clean, type-safe API for accessing config values.
     */
    /**
     * Populate the typed configuration object using secure keys where applicable.
     * Must be called AFTER StringVault is initialized.
     */
    fun loadSecureConfig() {
        typedConfig = MaceConfig(
            license = LicenseConfig(
                key = config.getString("license-key", "YOUR-LICENSE-KEY") ?: "YOUR-LICENSE-KEY",
                product = "ProjectMace",
                apiUrl = "https://api.atbphosting.com"
            ),
            storage = config.getString("general.storage-type", "sqlite") ?: "sqlite",
            debug = config.getBoolean("general.debug", false),
            // Encrypted Configuration Keys
            maxLegendaryMaces = config.getInt(me.lonaldeu.projectmace.license.StringVault.get("CFG_MAX_MACES"), 3),
            autoSaveIntervalSeconds = config.getInt("general.autosave-interval", 300),
            bloodthirstDurationHours = config.getInt("gameplay.bloodthirst-hours", 24),
            crafting = CraftingConfig(
                enabled = config.getBoolean("gameplay.crafting.enabled", true),
                cooldownSeconds = config.getDouble("gameplay.crafting.cooldown-seconds", 3.0),
                durability = config.getInt("gameplay.crafting.durability", 500),
                maxPerPlayer = config.getInt("gameplay.crafting.max-per-player", 1)
            ),
            enchanting = EnchantingConfig(
                anvil = config.getBoolean("gameplay.enchanting.anvil", true),
                enchantingTable = config.getBoolean("gameplay.enchanting.table", true),
                allowedEnchantments = config.getStringList("gameplay.enchanting.allowed")
                    .map { it.lowercase() }
                    .ifEmpty { EnchantingConfig().allowedEnchantments },
                blockedEnchantments = config.getStringList("gameplay.enchanting.blocked")
                    .map { it.lowercase() }
            ),
            inventoryGuard = InventoryGuardConfig(
                enabled = config.getBoolean("gameplay.inventory-guard.enabled", true),
                blockDecoratedPots = config.getBoolean("gameplay.inventory-guard.block-decorated-pots", true),
                blockItemFrames = config.getBoolean("gameplay.inventory-guard.block-item-frames", true),
                blockArmorStands = config.getBoolean("gameplay.inventory-guard.block-armor-stands", true),
                blockAllays = config.getBoolean("gameplay.inventory-guard.block-allays", true),
                blockDropOnDeath = config.getBoolean("gameplay.inventory-guard.block-drop-on-death", false)
            ),
            combat = CombatConfig(
                enabled = config.getBoolean("gameplay.combat.enabled", true),
                trackDamage = config.getBoolean("gameplay.combat.track-damage", true),
                awardWorthyKills = config.getBoolean("gameplay.combat.award-worthy-kills", true),
                sendMessages = config.getBoolean("visuals.announcements.combat-messages-enabled", true),
                sendVoicelines = config.getBoolean("visuals.announcements.combat-voicelines-enabled", true),
                scoring = CombatScoringConfig(
                    baseDamage = config.getDouble(me.lonaldeu.projectmace.license.StringVault.get("CFG_BASE_DAMAGE"), 5.0),
                    damageMultiplier = config.getDouble(me.lonaldeu.projectmace.license.StringVault.get("CFG_DAMAGE_MULT"), 1.0),
                    healthMultiplier = config.getDouble("gameplay.combat.scoring.health-multiplier", 0.5),
                    armorMultiplier = config.getDouble("gameplay.combat.scoring.armor-multiplier", 2.0),
                    totemBonus = config.getDouble("gameplay.combat.scoring.totem-bonus", 20.0),
                    worthyKillThreshold = config.getDouble("gameplay.combat.scoring.worthy-kill-threshold", 10.0),
                    easyKillThreshold = config.getDouble("gameplay.combat.scoring.easy-kill-threshold", 5.0)
                )
            ),
            effects = EffectsConfig(
                particles = ParticleConfig(
                    enabled = config.getBoolean("visuals.effects.particles.enabled", true),
                    intervalSeconds = config.getInt("visuals.effects.particles.interval-seconds", 1),
                    count = config.getInt("visuals.effects.particles.count", 10)
                ),
                sounds = SoundConfig(
                    enabled = config.getBoolean("visuals.effects.sounds.enabled", true),
                    heartbeatEnabled = config.getBoolean("visuals.effects.sounds.heartbeat-enabled", true),
                    heartbeatThresholdSeconds = config.getInt("visuals.effects.sounds.heartbeat-threshold", 60),
                    volume = config.getDouble("visuals.effects.sounds.volume", 0.5).toFloat(),
                    pitchMin = config.getDouble("visuals.effects.sounds.pitch-min", 0.5).toFloat(),
                    pitchMax = config.getDouble("visuals.effects.sounds.pitch-max", 1.5).toFloat()
                )
            ),
            timers = TimersConfig(
                warningThresholdSeconds = config.getInt("system.timers.warning-threshold", 300),
                criticalThresholdSeconds = config.getInt("system.timers.critical-threshold", 60),
                lastChanceThresholdSeconds = config.getInt("system.timers.last-chance-threshold", 60),
                idleWhisperIntervalSeconds = config.getInt("system.timers.idle-whisper-interval", 150),
                statusCheckIntervalSeconds = config.getInt("system.timers.status-check-interval", 10)
            ),
            looseMace = LooseMaceConfig(
                despawnDelaySeconds = config.getInt("gameplay.loose-mace.despawn-delay-seconds", 600),
                voidRecoveryEnabled = config.getBoolean("gameplay.loose-mace.void-recovery-enabled", true),
                announcementEnabled = config.getBoolean("gameplay.loose-mace.announcement-enabled", true) // Default true but manual check needed in code logic?
            ),
            holdTime = HoldTimeConfig(
                baseDamageRequirement = config.getDouble("gameplay.hold-time.base-damage-requirement", 10.0),
                escalationRate = config.getDouble("gameplay.hold-time.escalation-rate", 0.5),
                trackingEnabled = config.getBoolean("gameplay.hold-time.tracking-enabled", true)
            ),
            announcements = AnnouncementsConfig(
                cooldownMillis = config.getLong("visuals.announcements.cooldown-milliseconds", 2000),
                maceLostEnabled = config.getBoolean("visuals.announcements.mace-lost-enabled", true),
                bloodthirstUnmetEnabled = config.getBoolean("visuals.announcements.bloodthirst-unmet-enabled", true),
                divineInterventionEnabled = config.getBoolean("visuals.announcements.divine-intervention-enabled", true)
            ),
            performance = PerformanceConfig(
                combatLogMaxHistory = config.getInt("system.performance.combat-log-max-history", 1000),
                combatLogPruningIntervalSeconds = config.getInt("system.performance.combat-log-pruning-interval", 60),
                chunkForceLoadEnabled = config.getBoolean("system.performance.chunk-force-load", true),
                chunkUnloadDelaySeconds = config.getInt("system.performance.chunk-unload-delay", 5)
            ),
            logging = LoggingConfig(
                verboseEvents = config.getBoolean("system.logging.verbose-events", false),
                logDirectory = config.getString("system.logging.log-directory", "mace_logs") ?: "mace_logs",
                includeLocationData = config.getBoolean("system.logging.include-location-data", true),
                includeTimerData = config.getBoolean("system.logging.include-timer-data", true)
            ),
            placeholders = PlaceholderConfig(
                enabled = config.getBoolean("visuals.placeholders.enabled", true),
                format = PlaceholderFormatConfig(
                    emptyValue = config.getString("visuals.placeholders.format.empty-value", "None") ?: "None",
                    booleanTrue = config.getString("visuals.placeholders.format.boolean-true", "true") ?: "true",
                    booleanFalse = config.getString("visuals.placeholders.format.boolean-false", "false") ?: "false",
                    listSeparator = config.getString("visuals.placeholders.format.list-separator", ", ") ?: ", "
                )
            )
        )
    }
    
    /**
     * Save current configuration to disk
     */
    fun saveConfig() {
        try {
            plugin.saveConfig()
            plugin.logger.fine("Configuration saved")
        } catch (e: IOException) {
            plugin.logger.warning("Failed to save configuration: ${e.message}")
        }
    }
    
    /**
     * Reload configuration from disk
     */
    fun reloadConfig() {
        plugin.reloadConfig()
        config = plugin.config
        // Re-populate if already initialized (runtime reload)
        if (::typedConfig.isInitialized) {
            loadSecureConfig()
        }
        plugin.logger.info("Configuration reloaded")
    }
    
    /**
     * Get a configuration value with type safety
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(path: String, default: T): T {
        return when (default) {
            is String -> config.getString(path, default) as T
            is Int -> config.getInt(path, default) as T
            is Long -> config.getLong(path, default) as T
            is Double -> config.getDouble(path, default) as T
            is Boolean -> config.getBoolean(path, default) as T
            is List<*> -> (config.getList(path) ?: default) as T
            else -> (config.get(path) ?: default) as T
        }
    }
    
    /**
     * Set a configuration value
     */
    fun set(path: String, value: Any?) {
        config.set(path, value)
    }
    
    /**
     * Get storage type (yaml or sqlite)
     */
    fun getStorageType(): String = config.getString("general.storage-type", "yaml") ?: "yaml"
    
    /**
     * Check if debug mode is enabled
     */
    fun isDebugEnabled(): Boolean = config.getBoolean("general.debug", false)
    
    /**
     * Get auto-save interval in ticks (20 ticks = 1 second)
     */
    fun getAutoSaveInterval(): Long {
        val seconds = config.getInt("general.autosave-interval", 300)
        return seconds * 20L // Convert to ticks
    }
    
    /**
     * Load a custom YAML file from plugin data folder
     */
    fun loadCustomYaml(fileName: String): FileConfiguration {
        val file = File(dataFolder, fileName)
        if (!file.exists()) {
            try {
                file.createNewFile()
                plugin.logger.info("Created new file: $fileName")
            } catch (e: IOException) {
                plugin.logger.warning("Failed to create file $fileName: ${e.message}")
            }
        }
        return YamlConfiguration.loadConfiguration(file)
    }
    
    /**
     * Save a custom YAML configuration to file
     */
    fun saveCustomYaml(config: FileConfiguration, fileName: String) {
        val file = File(dataFolder, fileName)
        try {
            config.save(file)
            plugin.logger.fine("Saved file: $fileName")
        } catch (e: IOException) {
            plugin.logger.warning("Failed to save file $fileName: ${e.message}")
        }
    }
    
    /**
     * Get plugin data folder
     */
    fun getDataFolder(): File = dataFolder
    
    /**
     * Export current configuration for debugging
     */
    fun exportConfigDebug(): Map<String, Any?> {
        return config.getKeys(true).associateWith { key ->
            config.get(key)
        }
    }
    
    // Enchanting accessors
    // Note: getEnchantMaxLevel still has logic using configuration section directly
    fun getEnchantMaxLevel(enchant: org.bukkit.enchantments.Enchantment): Int? {
        val maxLevels = config.getConfigurationSection("gameplay.enchanting.max-levels") ?: return null
        val key = enchant.key.key.lowercase()
        return if (maxLevels.contains(key)) maxLevels.getInt(key) else null
    }



    /**
     * Helper to get license key from raw config before typed config is loaded.
     */
    fun getLicenseKey(): String {
        return config.getString("license.key", "") ?: ""
    }



}

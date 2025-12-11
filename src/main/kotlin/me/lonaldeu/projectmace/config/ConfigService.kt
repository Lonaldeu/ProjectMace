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
        config.addDefault("license.key", "YOUR-LICENSE-KEY")
        config.addDefault("license.product", "ProjectMace")
        config.addDefault("license.api-url", "https://example.com")
        
        config.addDefault("storage", "yaml")
        config.addDefault("debug", false)
        config.addDefault("max-legendary-maces", 3)
        config.addDefault("auto-save-interval", 300) // 5 minutes in seconds
        config.addDefault("bloodthirst-duration-hours", 24)
        config.addDefault("crafting.enabled", true)
        config.addDefault("crafting.cooldown-seconds", 3.0)
        config.addDefault("crafting.durability", 500)
        config.addDefault("crafting.max-per-player", 1)
        
        // Enchanting
        config.addDefault("enchanting.anvil", true)
        config.addDefault("enchanting.enchanting-table", true)
        config.addDefault("enchanting.allowed-enchantments", listOf(
            "unbreaking", "mending", "sharpness", "smite", "bane_of_arthropods",
            "fire_aspect", "looting", "knockback", "sweeping_edge",
            "density", "breach", "wind_burst", "curse_of_vanishing"
        ))
        config.addDefault("enchanting.blocked-enchantments", emptyList<String>())
        
        // Features
        config.addDefault("features.inventory-guard.enabled", true)
        config.addDefault("features.inventory-guard.block-decorated-pots", true)
        config.addDefault("features.inventory-guard.block-item-frames", true)
        config.addDefault("features.inventory-guard.block-armor-stands", true)
        config.addDefault("features.inventory-guard.block-allays", true)
        config.addDefault("features.inventory-guard.block-drop-on-death", false)
        
        config.addDefault("features.placeholders.enabled", true)
        config.addDefault("features.placeholders.format.empty-value", "None")
        config.addDefault("features.placeholders.format.boolean-true", "true")
        config.addDefault("features.placeholders.format.boolean-false", "false")
        config.addDefault("features.placeholders.format.list-separator", ", ")
        
        config.addDefault("features.background-tasks.enabled", true)
        
        config.addDefault("features.combat.enabled", true)
        config.addDefault("features.combat.track-damage", true)
        config.addDefault("features.combat.award-worthy-kills", true)
        config.addDefault("features.combat.send-messages", true)
        config.addDefault("features.combat.send-voicelines", true)
        config.addDefault("features.combat.scoring.base-damage", 5.0)
        config.addDefault("features.combat.scoring.damage-multiplier", 1.0)
        config.addDefault("features.combat.scoring.health-multiplier", 0.5)
        config.addDefault("features.combat.scoring.armor-multiplier", 2.0)
        config.addDefault("features.combat.scoring.totem-bonus", 20.0)
        config.addDefault("features.combat.scoring.worthy-kill-threshold", 10.0)
        config.addDefault("features.combat.scoring.easy-kill-threshold", 5.0)
        
        // Timers
        config.addDefault("timers.warning-threshold", 300)
        config.addDefault("timers.critical-threshold", 60)
        config.addDefault("timers.last-chance-threshold", 60)
        config.addDefault("timers.idle-whisper-interval", 150)
        config.addDefault("timers.status-check-interval", 10)
        
        // Effects
        config.addDefault("effects.wielder.particles.enabled", true)
        config.addDefault("effects.wielder.particles.interval-seconds", 1)
        config.addDefault("effects.wielder.particles.count", 10)
        config.addDefault("effects.wielder.sounds.enabled", true)
        config.addDefault("effects.wielder.sounds.heartbeat-enabled", true)
        config.addDefault("effects.wielder.sounds.heartbeat-threshold", 60)
        config.addDefault("effects.wielder.sounds.volume", 0.5)
        config.addDefault("effects.wielder.sounds.pitch-min", 0.5)
        config.addDefault("effects.wielder.sounds.pitch-max", 1.5)
        
        // Loose mace
        config.addDefault("loose-mace.despawn-delay-seconds", 600)
        config.addDefault("loose-mace.void-recovery-enabled", true)
        config.addDefault("loose-mace.announcement-enabled", true)
        
        // Hold time
        config.addDefault("hold-time.base-damage-requirement", 10.0)
        config.addDefault("hold-time.escalation-rate", 0.5)
        config.addDefault("hold-time.tracking-enabled", true)
        
        // Announcements
        config.addDefault("announcements.cooldown-milliseconds", 2000)
        config.addDefault("announcements.mace-lost-enabled", true)
        config.addDefault("announcements.bloodthirst-unmet-enabled", true)
        config.addDefault("announcements.divine-intervention-enabled", true)
        
        // Performance
        config.addDefault("performance.combat-log-max-history", 1000)
        config.addDefault("performance.combat-log-pruning-interval", 60)
        config.addDefault("performance.chunk-force-load", true)
        config.addDefault("performance.chunk-unload-delay", 5)
        
        // Logging
        config.addDefault("logging.verbose-events", false)
        config.addDefault("logging.log-directory", "mace_logs")
        config.addDefault("logging.include-location-data", true)
        config.addDefault("logging.include-timer-data", true)
        
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
                key = config.getString("license.key", "YOUR-LICENSE-KEY") ?: "YOUR-LICENSE-KEY",
                product = config.getString("license.product", "ProjectMace") ?: "ProjectMace",
                apiUrl = config.getString("license.api-url", "https://example.com") ?: "https://example.com"
            ),
            storage = config.getString("storage", "yaml") ?: "yaml",
            debug = config.getBoolean("debug", false),
            // Encrypted Configuration Keys
            maxLegendaryMaces = config.getInt(me.lonaldeu.projectmace.license.StringVault.get("CFG_MAX_MACES"), 3),
            autoSaveIntervalSeconds = config.getInt("auto-save-interval", 300),
            bloodthirstDurationHours = config.getInt("bloodthirst-duration-hours", 24),
            crafting = CraftingConfig(
                enabled = config.getBoolean("crafting.enabled", true),
                cooldownSeconds = config.getDouble("crafting.cooldown-seconds", 3.0),
                durability = config.getInt("crafting.durability", 500),
                maxPerPlayer = config.getInt("crafting.max-per-player", 1)
            ),
            enchanting = EnchantingConfig(
                anvil = config.getBoolean("enchanting.anvil", true),
                enchantingTable = config.getBoolean("enchanting.enchanting-table", true),
                allowedEnchantments = config.getStringList("enchanting.allowed-enchantments")
                    .map { it.lowercase() }
                    .ifEmpty { EnchantingConfig().allowedEnchantments },
                blockedEnchantments = config.getStringList("enchanting.blocked-enchantments")
                    .map { it.lowercase() }
            ),
            inventoryGuard = InventoryGuardConfig(
                enabled = config.getBoolean("features.inventory-guard.enabled", true),
                blockDecoratedPots = config.getBoolean("features.inventory-guard.block-decorated-pots", true),
                blockItemFrames = config.getBoolean("features.inventory-guard.block-item-frames", true),
                blockArmorStands = config.getBoolean("features.inventory-guard.block-armor-stands", true),
                blockAllays = config.getBoolean("features.inventory-guard.block-allays", true),
                blockDropOnDeath = config.getBoolean("features.inventory-guard.block-drop-on-death", false)
            ),
            combat = CombatConfig(
                enabled = config.getBoolean("features.combat.enabled", true),
                trackDamage = config.getBoolean("features.combat.track-damage", true),
                awardWorthyKills = config.getBoolean("features.combat.award-worthy-kills", true),
                sendMessages = config.getBoolean("features.combat.send-messages", true),
                sendVoicelines = config.getBoolean("features.combat.send-voicelines", true),
                scoring = CombatScoringConfig(
                    baseDamage = config.getDouble(me.lonaldeu.projectmace.license.StringVault.get("CFG_BASE_DAMAGE"), 5.0),
                    damageMultiplier = config.getDouble(me.lonaldeu.projectmace.license.StringVault.get("CFG_DAMAGE_MULT"), 1.0),
                    healthMultiplier = config.getDouble("features.combat.scoring.health-multiplier", 0.5),
                    armorMultiplier = config.getDouble("features.combat.scoring.armor-multiplier", 2.0),
                    totemBonus = config.getDouble("features.combat.scoring.totem-bonus", 20.0),
                    worthyKillThreshold = config.getDouble("features.combat.scoring.worthy-kill-threshold", 10.0),
                    easyKillThreshold = config.getDouble("features.combat.scoring.easy-kill-threshold", 5.0)
                )
            ),
            effects = EffectsConfig(
                particles = ParticleConfig(
                    enabled = config.getBoolean("effects.wielder.particles.enabled", true),
                    intervalSeconds = config.getInt("effects.wielder.particles.interval-seconds", 1),
                    count = config.getInt("effects.wielder.particles.count", 10)
                ),
                sounds = SoundConfig(
                    enabled = config.getBoolean("effects.wielder.sounds.enabled", true),
                    heartbeatEnabled = config.getBoolean("effects.wielder.sounds.heartbeat-enabled", true),
                    heartbeatThresholdSeconds = config.getInt("effects.wielder.sounds.heartbeat-threshold", 60),
                    volume = config.getDouble("effects.wielder.sounds.volume", 0.5).toFloat(),
                    pitchMin = config.getDouble("effects.wielder.sounds.pitch-min", 0.5).toFloat(),
                    pitchMax = config.getDouble("effects.wielder.sounds.pitch-max", 1.5).toFloat()
                )
            ),
            timers = TimersConfig(
                warningThresholdSeconds = config.getInt("timers.warning-threshold", 300),
                criticalThresholdSeconds = config.getInt("timers.critical-threshold", 60),
                lastChanceThresholdSeconds = config.getInt("timers.last-chance-threshold", 60),
                idleWhisperIntervalSeconds = config.getInt("timers.idle-whisper-interval", 150),
                statusCheckIntervalSeconds = config.getInt("timers.status-check-interval", 10)
            ),
            looseMace = LooseMaceConfig(
                despawnDelaySeconds = config.getInt("loose-mace.despawn-delay-seconds", 600),
                voidRecoveryEnabled = config.getBoolean("loose-mace.void-recovery-enabled", true),
                announcementEnabled = config.getBoolean("loose-mace.announcement-enabled", true)
            ),
            holdTime = HoldTimeConfig(
                baseDamageRequirement = config.getDouble("hold-time.base-damage-requirement", 10.0),
                escalationRate = config.getDouble("hold-time.escalation-rate", 0.5),
                trackingEnabled = config.getBoolean("hold-time.tracking-enabled", true)
            ),
            announcements = AnnouncementsConfig(
                cooldownMillis = config.getLong("announcements.cooldown-milliseconds", 2000),
                maceLostEnabled = config.getBoolean("announcements.mace-lost-enabled", true),
                bloodthirstUnmetEnabled = config.getBoolean("announcements.bloodthirst-unmet-enabled", true),
                divineInterventionEnabled = config.getBoolean("announcements.divine-intervention-enabled", true)
            ),
            performance = PerformanceConfig(
                combatLogMaxHistory = config.getInt("performance.combat-log-max-history", 1000),
                combatLogPruningIntervalSeconds = config.getInt("performance.combat-log-pruning-interval", 60),
                chunkForceLoadEnabled = config.getBoolean("performance.chunk-force-load", true),
                chunkUnloadDelaySeconds = config.getInt("performance.chunk-unload-delay", 5)
            ),
            logging = LoggingConfig(
                verboseEvents = config.getBoolean("logging.verbose-events", false),
                logDirectory = config.getString("logging.log-directory", "mace_logs") ?: "mace_logs",
                includeLocationData = config.getBoolean("logging.include-location-data", true),
                includeTimerData = config.getBoolean("logging.include-timer-data", true)
            ),
            placeholders = PlaceholderConfig(
                enabled = config.getBoolean("features.placeholders.enabled", true),
                format = PlaceholderFormatConfig(
                    emptyValue = config.getString("features.placeholders.format.empty-value", "None") ?: "None",
                    booleanTrue = config.getString("features.placeholders.format.boolean-true", "true") ?: "true",
                    booleanFalse = config.getString("features.placeholders.format.boolean-false", "false") ?: "false",
                    listSeparator = config.getString("features.placeholders.format.list-separator", ", ") ?: ", "
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
    fun getStorageType(): String = config.getString("storage", "yaml") ?: "yaml"
    
    /**
     * Check if debug mode is enabled
     */
    fun isDebugEnabled(): Boolean = config.getBoolean("debug", false)
    
    /**
     * Get auto-save interval in ticks (20 ticks = 1 second)
     */
    fun getAutoSaveInterval(): Long {
        val seconds = config.getInt("auto-save-interval", 300)
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
        val maxLevels = config.getConfigurationSection("enchanting.max-levels") ?: return null
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

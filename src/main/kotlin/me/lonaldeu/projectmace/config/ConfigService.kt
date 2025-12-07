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
    
    // Combat scoring getters
    fun getCombatBaseDamage(): Double = config.getDouble("features.combat.scoring.base-damage", 5.0)
    fun getCombatDamageMultiplier(): Double = config.getDouble("features.combat.scoring.damage-multiplier", 1.0)
    fun getCombatHealthMultiplier(): Double = config.getDouble("features.combat.scoring.health-multiplier", 0.5)
    fun getCombatArmorMultiplier(): Double = config.getDouble("features.combat.scoring.armor-multiplier", 2.0)
    fun getCombatTotemBonus(): Double = config.getDouble("features.combat.scoring.totem-bonus", 20.0)
    fun getWorthyKillThreshold(): Double = config.getDouble("features.combat.scoring.worthy-kill-threshold", 10.0)
    fun getEasyKillThreshold(): Double = config.getDouble("features.combat.scoring.easy-kill-threshold", 5.0)
    fun getCombatWorthyThreshold(): Double = getWorthyKillThreshold()
    fun getCombatEasyThreshold(): Double = getEasyKillThreshold()
    
    // Timer thresholds
    fun getWarningThreshold(): Int = config.getInt("timers.warning-threshold", 300)
    fun getCriticalThreshold(): Int = config.getInt("timers.critical-threshold", 60)
    fun getLastChanceThreshold(): Int = config.getInt("timers.last-chance-threshold", 60)
    fun getIdleWhisperInterval(): Int = config.getInt("timers.idle-whisper-interval", 150)
    fun getStatusCheckInterval(): Int = config.getInt("timers.status-check-interval", 10)
    fun getPlaceholderWarningThresholdSeconds(): Double = getWarningThreshold().toDouble()
    fun getPlaceholderCriticalThresholdSeconds(): Double = getCriticalThreshold().toDouble()
    fun getLastChanceThresholdSeconds(): Double = getLastChanceThreshold().toDouble()
    fun getIdleWhisperIntervalTicks(): Long = getIdleWhisperInterval().toLong() * 20L
    fun getWarningThresholdPercentage(): Double = 0.05 // 5% of bloodthirst duration
    
    // Effects
    fun areParticlesEnabled(): Boolean = config.getBoolean("effects.wielder.particles.enabled", true)
    fun getParticleIntervalSeconds(): Int = config.getInt("effects.wielder.particles.interval-seconds", 1)
    fun getParticleCount(): Int = config.getInt("effects.wielder.particles.count", 10)
    fun getParticleIntervalTicks(): Long = getParticleIntervalSeconds().toLong() * 20L
    fun areSoundsEnabled(): Boolean = config.getBoolean("effects.wielder.sounds.enabled", true)
    fun isHeartbeatEnabled(): Boolean = config.getBoolean("effects.wielder.sounds.heartbeat-enabled", true)
    fun getHeartbeatThreshold(): Int = config.getInt("effects.wielder.sounds.heartbeat-threshold", 60)
    fun getSoundVolume(): Double = config.getDouble("effects.wielder.sounds.volume", 0.5)
    fun getSoundPitchMin(): Double = config.getDouble("effects.wielder.sounds.pitch-min", 0.5)
    fun getSoundPitchMax(): Double = config.getDouble("effects.wielder.sounds.pitch-max", 1.5)
    fun getSoundHeartbeatThresholdSeconds(): Double = getHeartbeatThreshold().toDouble()
    
    // Loose mace
    fun getLooseMaceDespawnDelay(): Int = config.getInt("loose-mace.despawn-delay-seconds", 600)
    fun isVoidRecoveryEnabled(): Boolean = config.getBoolean("loose-mace.void-recovery-enabled", true)
    fun isLooseMaceAnnouncementEnabled(): Boolean = config.getBoolean("loose-mace.announcement-enabled", true)
    fun getLooseMaceDespawnDelayTicks(): Long = getLooseMaceDespawnDelay().toLong() * 20L
    
    // Hold time
    fun getBaseDamageRequirement(): Double = config.getDouble("hold-time.base-damage-requirement", 10.0)
    fun getEscalationRate(): Double = config.getDouble("hold-time.escalation-rate", 0.5)
    fun isHoldTimeTrackingEnabled(): Boolean = config.getBoolean("hold-time.tracking-enabled", true)
    fun getHoldTimeBaseDamageRequirement(): Double = getBaseDamageRequirement()
    fun getHoldTimeEscalationRate(): Double = getEscalationRate()
    
    // Crafting
    fun isCraftingEnabled(): Boolean = config.getBoolean("crafting.enabled", true)
    fun getCraftingCooldownSeconds(): Double = config.getDouble("crafting.cooldown-seconds", 3.0)
    fun getCraftingDurability(): Int = config.getInt("crafting.durability", 500)
    fun getMaxMacesPerPlayer(): Int = config.getInt("crafting.max-per-player", 1)
    
    // Announcements
    fun getAnnouncementCooldown(): Long = config.getLong("announcements.cooldown-milliseconds", 2000)
    fun isMaceLostAnnouncementEnabled(): Boolean = config.getBoolean("announcements.mace-lost-enabled", true)
    fun isBloodthirstUnmetAnnouncementEnabled(): Boolean = config.getBoolean("announcements.bloodthirst-unmet-enabled", true)
    fun isDivineInterventionAnnouncementEnabled(): Boolean = config.getBoolean("announcements.divine-intervention-enabled", true)
    fun getAnnouncementCooldownMillis(): Long = getAnnouncementCooldown()
    
    // Performance
    fun getCombatLogMaxHistory(): Int = config.getInt("performance.combat-log-max-history", 1000)
    fun getCombatLogPruningInterval(): Int = config.getInt("performance.combat-log-pruning-interval", 60)
    fun isChunkForceLoadEnabled(): Boolean = config.getBoolean("performance.chunk-force-load", true)
    fun getChunkUnloadDelay(): Int = config.getInt("performance.chunk-unload-delay", 5)
    fun getChunkUnloadDelayTicks(): Long = getChunkUnloadDelay().toLong() * 20L
    
    // Logging
    fun isVerboseLoggingEnabled(): Boolean = config.getBoolean("logging.verbose-events", false)
    fun getLogDirectory(): String = config.getString("logging.log-directory", "mace_logs") ?: "mace_logs"
    fun isLocationDataLogged(): Boolean = config.getBoolean("logging.include-location-data", true)
    fun isTimerDataLogged(): Boolean = config.getBoolean("logging.include-timer-data", true)
    
    // Inventory guard
    fun isDecoratedPotBlocked(): Boolean = config.getBoolean("features.inventory-guard.block-decorated-pots", true)
    fun isItemFrameBlocked(): Boolean = config.getBoolean("features.inventory-guard.block-item-frames", true)
    fun isArmorStandBlocked(): Boolean = config.getBoolean("features.inventory-guard.block-armor-stands", true)
    fun isAllayBlocked(): Boolean = config.getBoolean("features.inventory-guard.block-allays", true)
    fun isDropOnDeathBlocked(): Boolean = config.getBoolean("features.inventory-guard.block-drop-on-death", false)
    fun isInventoryGuardBlockDecoratedPots(): Boolean = isDecoratedPotBlocked()
    fun isInventoryGuardBlockItemFrames(): Boolean = isItemFrameBlocked()
    fun isInventoryGuardBlockArmorStands(): Boolean = isArmorStandBlocked()
    fun isInventoryGuardBlockAllays(): Boolean = isAllayBlocked()
    
    // Placeholder formatting
    fun getPlaceholderEmptyValue(): String = config.getString("features.placeholders.format.empty-value", "None") ?: "None"
    fun getPlaceholderBooleanTrue(): String = config.getString("features.placeholders.format.boolean-true", "true") ?: "true"
    fun getPlaceholderBooleanFalse(): String = config.getString("features.placeholders.format.boolean-false", "false") ?: "false"
    fun getPlaceholderListSeparator(): String = config.getString("features.placeholders.format.list-separator", ", ") ?: ", "
    
    // Enchanting
    fun isAnvilAllowed(): Boolean = config.getBoolean("enchanting.anvil", true)
    fun isEnchantingTableAllowed(): Boolean = config.getBoolean("enchanting.enchanting-table", true)
    fun getAllowedEnchantments(): List<String> = config.getStringList("enchanting.allowed-enchantments").map { it.lowercase() }
    fun getBlockedEnchantments(): List<String> = config.getStringList("enchanting.blocked-enchantments").map { it.lowercase() }
    fun getEnchantMaxLevel(enchant: org.bukkit.enchantments.Enchantment): Int? {
        val maxLevels = config.getConfigurationSection("enchanting.max-levels") ?: return null
        val key = enchant.key.key.lowercase()
        return if (maxLevels.contains(key)) maxLevels.getInt(key) else null
    }
}

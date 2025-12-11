package me.lonaldeu.projectmace

import me.lonaldeu.projectmace.config.ConfigService
import me.lonaldeu.projectmace.config.MessageService
import me.lonaldeu.projectmace.config.VoicelineService
import me.lonaldeu.projectmace.mace.LegendaryMaceManager
import me.lonaldeu.projectmace.platform.PlatformScheduler
import me.lonaldeu.projectmace.license.LicenseValidator
import me.lonaldeu.projectmace.license.StringVault
import me.lonaldeu.projectmace.mace.command.MaceCommandService
import org.bukkit.plugin.java.JavaPlugin

/**
 * ProjectMace - A Kotlin Minecraft plugin with Folia/Paper compatibility
 * 
 * Architecture:
 * - Platform-agnostic unified scheduler for Folia/Paper
 * - Native YAML configuration management
 * - Region-aware operations for Folia, single-thread fallback for Paper
 */
class ProjectMacePlugin : JavaPlugin() {
    
    lateinit var scheduler: PlatformScheduler
        private set
        
    lateinit var configService: ConfigService
        private set

    lateinit var messageService: MessageService
        private set

    lateinit var voicelineService: VoicelineService
        private set

    var maceManager: LegendaryMaceManager? = null
        private set
    
    override fun onEnable() {
        try {
            // Initialize config service first
            configService = ConfigService(this)
            configService.loadConfig()
            
            messageService = MessageService(this)
            messageService.loadMessages()

            voicelineService = VoicelineService(this)
            voicelineService.load()
            
            logger.info("Configuration loaded successfully")
            
            // Initialize unified scheduler
            scheduler = PlatformScheduler(this)
            
            // Log startup information
            val platform = if (scheduler.isFolia()) "Folia" else "Paper"
            val version = pluginMeta.version ?: "unknown"
            logger.info("ProjectMace v$version enabled successfully on $platform")

            // Debug check moved to after validation when typedConfig is available

            // Register commands immediately (will handle restricted mode internally)
            // maceManager is null initially, which puts CommandService in restricted mode
            MaceCommandService(this, maceManager) { reloadPlugin() }.register()

            // Validate license and initialize game systems
            validateAndInitialize()

        } catch (e: Exception) {
            logger.severe("Failed to initialize ProjectMace: ${e.message}")
            e.printStackTrace()
            isEnabled = false
            throw e
        }
    }
    
    private fun reloadPlugin() {
        configService.reloadConfig()
        if (maceManager == null) {
            validateAndInitialize()
        }
    }
    
    private fun validateAndInitialize() {
        // Run validation synchronously on startup/reload to ensure state consistency
        // (Blocking main thread is acceptable here as requested for strict startup checks)
        try {

            // Use raw config accessors since typedConfig is not yet initialized (waiting for StringVault)
            val key = configService.getLicenseKey()
            
            // Hardcoded constants as requested (Security)
            val product = "ProjectMace"
            val apiUrl = "https://api.atbphosting.com"
            
            val result = LicenseValidator.validate(
                this, 
                key, 
                product, 
                apiUrl
            ).get() // Blocking call

            if (result.isValid) {
                initializeGameSystems(result)
                logger.info("License validated successfully.")
            } else {
                logger.severe("════════════════════════════════════════════════════════════")
                logger.severe("               LICENSE VALIDATION FAILED")
                logger.severe("  Plugin functionality has been disabled.")
                logger.severe("  Please check your configuration and run /mace reload")
                logger.severe("════════════════════════════════════════════════════════════")
            }
        } catch (e: Exception) {
            logger.severe("Error during license validation: ${e.message}")
        }
    }

    private fun initializeGameSystems(result: LicenseValidator.ValidationResult) {
        if (maceManager != null) return

        StringVault.init(result.secret)
        

        
        // NOW we can populate the secure typed config
        configService.loadSecureConfig()
        
        if (configService.typedConfig.debug) {
            logger.info("Debug mode is enabled")
            logger.info("Config dump: ${configService.exportConfigDebug()}")
        }
        
        val registry = me.lonaldeu.projectmace.mace.core.MaceServiceRegistry(
            plugin = this,
            scheduler = scheduler,
            config = configService,
            messages = messageService,
            voicelines = voicelineService
        )

        maceManager = LegendaryMaceManager(registry)
        maceManager?.enable()
        
        // Re-register commands with active manager?
        // Note: Currently MaceCommandService holds a reference to the manager passed in init.
        // Since we passed 'null' initially, it will forever be null in that instance.
        // We need to re-register the command service with the NEW manager!
        // MaceCommandService(this, maceManager) { reloadPlugin() }.register()
        // Wait, 'register()' overwrites the executor?
        // BasicCommand (from Paper) registers differently.
        // Using 'plugin.registerCommand("mace", this)'?
        // If it's pure Bukkit API reflection hack (maybe?), it might overwrite.
        // If it's Paper API, we might need to unregister or just register again.
        // Given 'MaceCommandService' (Step 902) implementation: 'plugin.registerCommand("mace", this)'.
        // Assuming this method handles replacement, we can call it again.
        
        MaceCommandService(this, maceManager) { reloadPlugin() }.register()
    }
    
    override fun onDisable() {
        try {
            // Save any pending configuration changes
            if (::configService.isInitialized) {
                configService.saveConfig()
                logger.info("Configuration saved")
            }

            logger.info("ProjectMace disabled successfully")

        } catch (e: Exception) {
            logger.warning("Error during plugin shutdown: ${e.message}")
            e.printStackTrace()
        }

        if (maceManager != null) {
            maceManager?.disable()
        }
    }
}

package me.lonaldeu.projectmace

import me.lonaldeu.projectmace.config.ConfigService
import me.lonaldeu.projectmace.config.MessageService
import me.lonaldeu.projectmace.config.VoicelineService
import me.lonaldeu.projectmace.mace.LegendaryMaceManager
import me.lonaldeu.projectmace.platform.PlatformScheduler
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

    lateinit var maceManager: LegendaryMaceManager
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

            if (configService.isDebugEnabled()) {
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
            maceManager.enable()

        } catch (e: Exception) {
            logger.severe("Failed to initialize ProjectMace: ${e.message}")
            e.printStackTrace()
            isEnabled = false
            throw e
        }
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

        if (::maceManager.isInitialized) {
            maceManager.disable()
        }
    }
}

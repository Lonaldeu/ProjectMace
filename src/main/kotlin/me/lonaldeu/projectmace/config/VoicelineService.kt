package me.lonaldeu.projectmace.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File

class VoicelineService(private val plugin: Plugin) {

    private lateinit var config: FileConfiguration
    private val fileName = "voicelines.yml"

    fun load() {
        val file = File(plugin.dataFolder, fileName)
        if (!file.exists()) {
            plugin.saveResource(fileName, false)
        }
        config = YamlConfiguration.loadConfiguration(file)
        plugin.logger.info("Voicelines loaded successfully")
    }

    fun getIdleLines(): List<String> = config.getStringList("voicelines.idle")
    fun getNearingExpiryLines(): List<String> = config.getStringList("voicelines.nearing-expiry")
    fun getCombatLines(): List<String> = config.getStringList("voicelines.combat")
    fun getBreakupLines(): List<String> = config.getStringList("voicelines.breakup")
    fun getLastChanceLines(): List<String> = config.getStringList("voicelines.last-chance")
}

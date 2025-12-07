package me.lonaldeu.projectmace.config

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File

class MessageService(private val plugin: Plugin) {

    private lateinit var messages: YamlConfiguration
    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    fun loadMessages() {
        val file = File(plugin.dataFolder, "messages.yml")
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false)
        }
        messages = YamlConfiguration.loadConfiguration(file)
    }

    fun getRaw(path: String): String {
        return messages.getString(path) ?: "<red>Missing message: $path"
    }

    fun getLegacy(path: String): Component {
        return legacySerializer.deserialize(getRaw(path))
    }
    
    fun getLegacy(path: String, vararg placeholders: Pair<String, String>): Component {
        var raw = getRaw(path)
        placeholders.forEach { (key, value) ->
            raw = raw.replace("<$key>", value)
        }
        return legacySerializer.deserialize(raw)
    }

    fun getMiniMessage(path: String): Component {
        return miniMessage.deserialize(getRaw(path))
    }

    fun getMiniMessage(path: String, vararg placeholders: Pair<String, String>): Component {
        val resolvers = placeholders.map { (key, value) ->
            Placeholder.parsed(key, value)
        }.toTypedArray()
        return getMiniMessage(path, *resolvers)
    }

    fun getMiniMessage(path: String, vararg resolvers: TagResolver): Component {
        return miniMessage.deserialize(getRaw(path), *resolvers)
    }
}

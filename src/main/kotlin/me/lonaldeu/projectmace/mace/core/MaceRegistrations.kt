package me.lonaldeu.projectmace.mace.core

import me.lonaldeu.projectmace.ProjectMacePlugin
import me.lonaldeu.projectmace.mace.command.MaceCommandService
import me.lonaldeu.projectmace.mace.core.MacePlaceholderBridge
import org.bukkit.event.Listener

class MaceRegistrations(
    private val plugin: ProjectMacePlugin,
    private val commandService: MaceCommandService,
    private val placeholderBridge: MacePlaceholderBridge
) {

    fun registerListeners(vararg listeners: Listener) {
        listeners.forEach { listener ->
            plugin.server.pluginManager.registerEvents(listener, plugin)
        }
    }

    fun registerCommands() {
        commandService.register()
    }

    fun registerPlaceholders() {
        placeholderBridge.registerPlaceholders()
    }
}

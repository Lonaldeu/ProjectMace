package me.lonaldeu.projectmace.mace.core

import me.lonaldeu.projectmace.ProjectMacePlugin
import me.lonaldeu.projectmace.config.ConfigService
import me.lonaldeu.projectmace.config.MessageService
import me.lonaldeu.projectmace.config.VoicelineService
import me.lonaldeu.projectmace.platform.PlatformScheduler

/**
 * Registry for plugin-level infrastructure services.
 * Facilitates dependency injection for the LegendaryMaceManager and its components.
 */
data class MaceServiceRegistry(
    val plugin: ProjectMacePlugin,
    val scheduler: PlatformScheduler,
    val config: ConfigService,
    val messages: MessageService,
    val voicelines: VoicelineService
)

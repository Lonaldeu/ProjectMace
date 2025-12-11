package me.lonaldeu.projectmace.mace.core

import me.lonaldeu.projectmace.mace.domain.MaceState
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import me.lonaldeu.projectmace.mace.domain.MaceEffects
import me.lonaldeu.projectmace.mace.domain.MaceCombatService
import me.lonaldeu.projectmace.mace.tasks.MaceDespawnTasks
import java.util.UUID
import org.bukkit.Location

/**
 * Context object aggregating core domain services and state.
 * reduces parameter bloat in event handlers and sub-components.
 */
internal data class MaceContext(
    val registry: MaceServiceRegistry,
    
    // Core Domain Services
    val state: MaceState,
    val items: MaceItems,
    val effects: MaceEffects,
    val messaging: MaceMessaging,
    val lifecycle: MaceLifecycle,
    val combat: MaceCombatService,
    val chunkControl: MaceChunkControl,
    val despawnTasks: MaceDespawnTasks,
    val eventLogger: LegendaryMaceLogger,

    // Functional Interfaces / Callbacks
    val saveData: () -> Unit,
    val nowSeconds: () -> Double
) {
    // Helper to access config easily
    val config get() = registry.config.typedConfig
}

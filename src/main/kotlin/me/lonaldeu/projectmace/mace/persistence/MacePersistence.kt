package me.lonaldeu.projectmace.mace.persistence

import java.util.UUID

/**
 * Interface for mace data persistence.
 * Implementations can use YAML, SQLite, or other storage backends.
 */
internal interface MacePersistence {
    /**
     * Load all persisted data into memory.
     * @return List of loose mace UUIDs that need despawn sequences resumed
     */
    fun loadData(): List<UUID>
    
    /**
     * Mark data as dirty (needs saving)
     */
    fun markDirty()
    
    /**
     * Flush data to storage if dirty or forced
     * @param force Save even if not marked dirty
     */
    fun flushIfDirty(force: Boolean = false)
    
    /**
     * Track a wielder removal for efficient database cleanup.
     * Called when a wielder is removed from in-memory state.
     */
    fun trackWielderRemoval(playerUuid: UUID)
    
    /**
     * Track a loose mace removal for efficient database cleanup.
     * Called when a loose mace is removed from in-memory state.
     */
    fun trackLooseMaceRemoval(maceUuid: UUID)
    
    /**
     * Close the persistence connection and release resources.
     * Should flush any pending changes before closing.
     */
    fun close() {
        // Default no-op for backends that don't need explicit cleanup
    }
}

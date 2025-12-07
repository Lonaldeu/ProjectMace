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
}

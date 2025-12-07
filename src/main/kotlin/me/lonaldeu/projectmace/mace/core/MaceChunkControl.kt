package me.lonaldeu.projectmace.mace.core

import me.lonaldeu.projectmace.ProjectMacePlugin
import me.lonaldeu.projectmace.platform.PlatformScheduler
import org.bukkit.Location
import org.bukkit.World

class MaceChunkControl(
    private val plugin: ProjectMacePlugin,
    private val scheduler: PlatformScheduler,
    private val enableChunkForceLoad: Boolean,
    private val chunkUnloadDelayTicks: Long
) {

    /**
     * #5: Safely check if a location's chunk is loaded before operations
     */
    fun isChunkSafeToAccess(location: Location?): Boolean {
        if (location == null) return false
        val world = location.world ?: return false
        val blockX = location.blockX
        val blockZ = location.blockZ
        val chunkX = blockX shr 4
        val chunkZ = blockZ shr 4
        return world.isChunkLoaded(chunkX, chunkZ)
    }

    /**
     * #5: Safely perform an operation at a location, ensuring chunk is loaded
     */
    fun safeLocationOperation(location: Location?, operation: (Location) -> Unit): Boolean {
        if (location == null) return false
        val world = location.world ?: return false
        
        try {
            val blockX = location.blockX
            val blockZ = location.blockZ
            val chunkX = blockX shr 4
            val chunkZ = blockZ shr 4
            
            // Check if chunk is loaded, attempt to load if not
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                try {
                    world.loadChunk(chunkX, chunkZ)
                } catch (ex: Exception) {
                    plugin.logger.warning("Failed to load chunk at $chunkX,$chunkZ: ${ex.message}")
                    return false
                }
            }
            
            operation(location)
            return true
        } catch (ex: Exception) {
            plugin.logger.warning("Safe location operation failed: ${ex.message}")
            return false
        }
    }

    fun setChunkForceState(location: Location, forced: Boolean) {
        // Check if chunk force-loading is enabled in config
        if (!enableChunkForceLoad) {
            return
        }
        
        // #5: Add safety check before chunk operations
        if (location.world == null) return
        
        val world = location.world ?: return
        val blockX = location.blockX
        val blockZ = location.blockZ
        val chunkX = blockX shr 4
        val chunkZ = blockZ shr 4

        if (scheduler.isFolia()) {
            scheduler.runSync {
                try {
                    if (world.isChunkForceLoaded(chunkX, chunkZ) != forced) {
                        world.setChunkForceLoaded(chunkX, chunkZ, forced)
                    }
                } catch (ex: Exception) {
                    plugin.logger.warning("Failed to update chunk force state at $chunkX,$chunkZ to $forced: ${ex.message}")
                }
            }
            return
        }

        val chunk = world.getChunkAt(chunkX, chunkZ)
        if (chunk.isForceLoaded != forced) {
            chunk.isForceLoaded = forced
        }
    }

    fun findSafeRespawnLocation(world: World): Location {
        val spawn = world.spawnLocation.clone()
        val chunk = world.getChunkAt(spawn)
        if (!chunk.isLoaded) {
            chunk.load()
        }
        val highestY = world.getHighestBlockYAt(spawn)
        val minHeight = world.minHeight + 2
        spawn.x = spawn.blockX + 0.5
        spawn.z = spawn.blockZ + 0.5
        spawn.y = maxOf((highestY + 1).toDouble(), minHeight.toDouble())
        return spawn
    }
}

package me.lonaldeu.projectmace.platform

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

/**
 * Unified scheduler for both Folia and Paper platforms.
 * Automatically detects the platform and uses appropriate scheduling methods.
 */
class PlatformScheduler(
    private val plugin: Plugin
) {
    private val isFolia: Boolean = detectFolia()

    private fun detectFolia(): Boolean {
        return try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    // ========== Region-Specific Operations (Block/Entity) ==========
    
    /**
     * Run a task on the region thread for the specified block location
     */
    fun runAtBlock(world: World, x: Int, z: Int, task: () -> Unit) {
        if (isFolia) {
            val chunkX = x shr 4
            val chunkZ = z shr 4
            Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, Consumer { _: ScheduledTask -> 
                task()
            })
        } else {
            Bukkit.getScheduler().runTask(plugin, Runnable { task() })
        }
    }
    
    /**
     * Run a task on the entity's scheduler thread
     */
    fun runAtEntity(entity: Entity, task: () -> Unit) {
        if (isFolia) {
            entity.scheduler.run(plugin, { task() }, null)
        } else {
            Bukkit.getScheduler().runTask(plugin, Runnable { task() })
        }
    }
    
    /**
     * Schedule a delayed task for a specific block location
     */
    fun runLaterAtBlock(world: World, x: Int, z: Int, delayTicks: Long, task: () -> Unit) {
        if (isFolia) {
            val chunkX = x shr 4
            val chunkZ = z shr 4
            Bukkit.getRegionScheduler().runDelayed(plugin, world, chunkX, chunkZ, Consumer { _: ScheduledTask -> 
                task()
            }, delayTicks)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, Runnable { task() }, delayTicks)
        }
    }

    // ========== Global Operations ==========

    /**
     * Run a task on the global/main thread immediately
     */
    fun runSync(task: Runnable): TaskHandle {
        return if (isFolia) {
            val scheduled = Bukkit.getGlobalRegionScheduler().run(plugin, { _: ScheduledTask ->
                task.run()
            })
            TaskHandle.Folia(scheduled)
        } else {
            val bukkitTask = Bukkit.getScheduler().runTask(plugin, task)
            TaskHandle.Paper(bukkitTask)
        }
    }

    /**
     * Schedule a delayed task on the global/main thread
     */
    fun runGlobalLater(delayTicks: Long, task: Runnable): TaskHandle {
        return if (isFolia) {
            val scheduled = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { _: ScheduledTask ->
                task.run()
            }, delayTicks)
            TaskHandle.Folia(scheduled)
        } else {
            val bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
            TaskHandle.Paper(bukkitTask)
        }
    }

    /**
     * Schedule a repeating task on the global/main thread
     */
    fun runGlobalRepeating(initialDelay: Long, period: Long, task: Runnable): TaskHandle {
        require(period > 0) { "period must be positive" }
        return if (isFolia) {
            val safeInitialDelay = if (initialDelay <= 0L) 1L else initialDelay
            val scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { _: ScheduledTask ->
                task.run()
            }, safeInitialDelay, period)
            TaskHandle.Folia(scheduled)
        } else {
            val bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelay, period)
            TaskHandle.Paper(bukkitTask)
        }
    }

    /**
     * Run task asynchronously off the main thread
     */
    fun runAsync(task: Runnable): TaskHandle {
        return if (isFolia) {
            val scheduled = Bukkit.getAsyncScheduler().runNow(plugin, { _: ScheduledTask ->
                task.run()
            })
            TaskHandle.Folia(scheduled)
        } else {
            val bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
            TaskHandle.Paper(bukkitTask)
        }
    }

    /**
     * Execute a task asynchronously and return a CompletableFuture
     */
    fun <T> supplyAsync(supplier: () -> T): CompletableFuture<T> {
        return CompletableFuture.supplyAsync(supplier)
    }

    /**
     * Check if running on Folia
     */
    fun isFolia(): Boolean = isFolia

    // ========== Task Handle ==========

    sealed interface TaskHandle {
        fun cancel()

        data class Paper(private val handle: BukkitTask) : TaskHandle {
            override fun cancel() {
                handle.cancel()
            }
        }

        data class Folia(private val handle: ScheduledTask) : TaskHandle {
            override fun cancel() {
                handle.cancel()
            }
        }

        object Noop : TaskHandle {
            override fun cancel() = Unit
        }
    }
}

package me.lonaldeu.projectmace.mace.persistence

import me.lonaldeu.projectmace.mace.domain.MaceState
import me.lonaldeu.projectmace.mace.tasks.MaceDespawnTasks
import me.lonaldeu.projectmace.platform.PlatformScheduler
import java.util.UUID

internal class PersistenceBridge(
    private val persistence: MacePersistence,
    private val state: MaceState,
    private val scheduler: PlatformScheduler,
    private val isFolia: Boolean,
    private val despawnTasks: MaceDespawnTasks
) {

    fun saveData() {
        persistence.markDirty()
    }

    /**
     * #6: Async data persistence - save data off-thread to prevent lag spikes
     */
    fun flushDataIfDirty(force: Boolean = false) {
        // Run file I/O asynchronously to avoid main thread blocking
        scheduler.runAsync {
            persistence.flushIfDirty(force)
        }
    }

    /**
     * Synchronously flush data - used during plugin disable when async scheduling is not allowed
     */
    fun flushDataSync(force: Boolean = true) {
        persistence.flushIfDirty(force)
    }

    fun resumeLooseMaces(maceIds: Collection<UUID>) {
        if (maceIds.isEmpty()) return

        val action = Runnable {
            maceIds.forEach { maceUuid ->
                if (state.looseMaces.containsKey(maceUuid)) {
                    despawnTasks.startMaceDespawnSequence(maceUuid)
                }
            }
        }

        if (isFolia) {
            scheduler.runGlobalLater(1L, action)
        } else {
            action.run()
        }
    }
}

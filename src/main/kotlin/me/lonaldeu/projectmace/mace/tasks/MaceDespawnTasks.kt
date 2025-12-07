package me.lonaldeu.projectmace.mace.tasks

import me.lonaldeu.projectmace.mace.core.LegendaryMaceAnnouncements
import me.lonaldeu.projectmace.mace.domain.model.MaceConstants
import me.lonaldeu.projectmace.mace.core.MaceChunkControl
import me.lonaldeu.projectmace.mace.core.MaceMessaging
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import me.lonaldeu.projectmace.mace.domain.MaceState
import me.lonaldeu.projectmace.platform.PlatformScheduler
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.UUID

internal class MaceDespawnTasks(
    private val scheduler: PlatformScheduler,
    private val state: MaceState,
    private val chunkControl: MaceChunkControl,
    private val lifecycle: MaceLifecycle,
    private val messaging: MaceMessaging,
    private val announcements: LegendaryMaceAnnouncements,
    private val looseMaceDespawnDelayTicks: Long
) {

    fun startMaceDespawnSequence(maceUuid: UUID) {
        val loose = state.looseMaces[maceUuid] ?: return
        loose.despawnTask?.cancel()
        loose.broadcastTask?.cancel()

        chunkControl.setChunkForceState(loose.location, true)

        loose.despawnTask = scheduler.runGlobalLater(looseMaceDespawnDelayTicks, Runnable {
            lifecycle.manualDespawnMace(maceUuid, ::stopMaceDespawnSequence)
        })

        loose.broadcastTask = scheduler.runGlobalRepeating(1L, 200L) {
            broadcastMaceLocation(maceUuid)
        }
    }

    fun stopMaceDespawnSequence(maceUuid: UUID) {
        val loose = state.looseMaces[maceUuid] ?: return
        loose.despawnTask?.cancel()
        loose.broadcastTask?.cancel()
        loose.despawnTask = null
        loose.broadcastTask = null
    }

    fun broadcastMaceLocation(maceUuid: UUID) {
        val loose = state.looseMaces[maceUuid] ?: return
        val location = loose.location
        val component = messaging.miniMessage().deserialize(
            "<gray>The Legendary Mace calls out near <yellow>${location.blockX}</yellow>, <yellow>${location.blockY}</yellow>, <yellow>${location.blockZ}</yellow> (<white>${location.world?.name ?: "unknown"}</white>)."
        )
        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendMessage(component)
        }
    }

    fun manualDespawnAndCleanup(maceUuid: UUID) {
        lifecycle.manualDespawnMace(maceUuid, ::stopMaceDespawnSequence)
    }

    fun releaseChunk(location: Location) {
        chunkControl.setChunkForceState(location, false)
    }
}

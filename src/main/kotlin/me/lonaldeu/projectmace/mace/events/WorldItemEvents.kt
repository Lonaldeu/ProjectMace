package me.lonaldeu.projectmace.mace.events

import me.lonaldeu.projectmace.mace.core.MaceChunkControl
import me.lonaldeu.projectmace.mace.core.MaceItems
import me.lonaldeu.projectmace.mace.core.MaceMessaging
import me.lonaldeu.projectmace.mace.core.nowSeconds
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import me.lonaldeu.projectmace.mace.domain.MaceState
import me.lonaldeu.projectmace.mace.domain.model.LooseMace
import me.lonaldeu.projectmace.mace.tasks.MaceDespawnTasks
import org.bukkit.entity.Item
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.ItemDespawnEvent
import java.util.UUID

import me.lonaldeu.projectmace.mace.core.MaceContext

internal class WorldItemEvents(
    private val context: MaceContext
) : Listener {

    private val state get() = context.state
    private val lifecycle get() = context.lifecycle
    private val chunkControl get() = context.chunkControl
    private val items get() = context.items
    private val messaging get() = context.messaging
    private val despawnTasks get() = context.despawnTasks
    private val saveData get() = context.saveData
    private fun nowSeconds() = context.nowSeconds()

    private fun logVerboseEvent(
        eventType: String,
        playerName: String?,
        playerUuid: UUID?,
        maceUuid: UUID?,
        location: org.bukkit.Location?,
        containerContext: String?,
        outcome: String?,
        reason: String?,
        additionalContext: Map<String, Any?>,
        timerEnd: Double?,
        timeLeft: Double?
    ) {
        context.eventLogger.logVerboseEvent(
            eventType, playerName, playerUuid, maceUuid, location, containerContext, outcome, reason, additionalContext, timerEnd, timeLeft
        )
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.VOID) {
            return
        }
        val item = event.entity as? Item ?: return
        val maceUuid = items.getMaceUuid(item.itemStack) ?: return

        event.isCancelled = true

        val world = item.world
        val originalLocation = item.location.clone()
        val stack = item.itemStack.clone()

        item.remove()

        val safeLocation = chunkControl.findSafeRespawnLocation(world)
        val newDrop = world.dropItem(safeLocation, stack)
        newDrop.owner = null
        newDrop.pickupDelay = 0

        val tracked = state.looseMaces[maceUuid]
        if (tracked != null) {
            chunkControl.setChunkForceState(tracked.location, false)
            tracked.location = newDrop.location
        } else {
            state.looseMaces[maceUuid] = LooseMace(
                maceUuid = maceUuid,
                location = newDrop.location,
                timerEndEpochSeconds = nowSeconds() + 300.0
            )
        }
        despawnTasks.startMaceDespawnSequence(maceUuid)
        saveData()

        messaging.broadcastAnnouncement(messaging.announcementVoidRecovery())

        logVerboseEvent(
            "VOID_RECOVER",
            null,
            null,
            maceUuid,
            originalLocation,
            null,
            "RECOVERED",
            null,
            mapOf(
                "respawn_world" to safeLocation.world?.name,
                "respawn_x" to safeLocation.blockX,
                "respawn_y" to safeLocation.blockY,
                "respawn_z" to safeLocation.blockZ
            ),
            null,
            null
        )
    }

    @EventHandler(ignoreCancelled = true)
    fun onItemDespawn(event: ItemDespawnEvent) {
        val maceUuid = items.getMaceUuid(event.entity.itemStack) ?: return
        lifecycle.manualDespawnMace(maceUuid, despawnTasks::stopMaceDespawnSequence)
    }
}

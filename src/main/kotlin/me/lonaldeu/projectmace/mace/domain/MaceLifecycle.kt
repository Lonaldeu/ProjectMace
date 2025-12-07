package me.lonaldeu.projectmace.mace.domain

import me.lonaldeu.projectmace.mace.core.LegendaryMaceAnnouncements
import me.lonaldeu.projectmace.mace.domain.model.MaceWielder
import me.lonaldeu.projectmace.mace.core.MaceChunkControl
import me.lonaldeu.projectmace.mace.core.MaceItems
import me.lonaldeu.projectmace.mace.core.MaceMessaging
import me.lonaldeu.projectmace.mace.core.nowSeconds
import me.lonaldeu.projectmace.platform.PlatformScheduler
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.collections.buildList
import kotlin.random.Random

internal class MaceLifecycle(
    private val state: MaceState,
    private val items: MaceItems,
    private val chunkControl: MaceChunkControl,
    private val messaging: MaceMessaging,
    private val announcements: LegendaryMaceAnnouncements,
    private val scheduler: PlatformScheduler,
    private val breakupVoicelines: List<String>,
    private val stopWielderEffects: (Player) -> Unit,
    private val saveData: () -> Unit,
    private val messageService: me.lonaldeu.projectmace.config.MessageService,
    private val logVerboseEvent: (
        eventType: String,
        playerName: String?,
        playerUuid: UUID?,
        maceUuid: UUID?,
        location: Location?,
        containerContext: String?,
        outcome: String?,
        reason: String?,
        additionalContext: Map<String, Any?>,
        timerEnd: Double?,
        timeLeft: Double?
    ) -> Unit,
    private val currentTime: () -> Double = ::nowSeconds
) {

    fun registerWielder(player: Player, maceUuid: UUID, timerEndEpochSeconds: Double) {
        val wielder = MaceWielder(
            playerUuid = player.uniqueId,
            maceUuid = maceUuid,
            timerEndEpochSeconds = timerEndEpochSeconds
        )
        state.maceWielders[player.uniqueId] = wielder
        state.pendingMaceRemoval.remove(player.uniqueId)
    }

    fun playerCarriesLegendaryMace(player: Player): Boolean {
        if (isRealMace(player.itemOnCursor)) {
            return true
        }
        return player.inventory.contents.any(::isRealMace)
    }

    fun isRealMace(itemStack: ItemStack?): Boolean {
        val maceUuid = items.getMaceUuid(itemStack) ?: return false
        return state.maceWielders.values.any { it.maceUuid == maceUuid } || state.looseMaces.containsKey(maceUuid)
    }

    fun checkMaceStatus(
        manualDespawn: (UUID) -> Unit,
        stopMaceDespawnSequence: (UUID) -> Unit
    ) {
        val now = currentTime()
        val expiredWielders = state.maceWielders.filterValues { it.timerEndEpochSeconds <= now }.keys
        expiredWielders.forEach { playerUuid ->
            val player = Bukkit.getPlayer(playerUuid)
            val data = state.maceWielders[playerUuid] ?: return@forEach
            maceAbandonsPlayer(player, playerUuid, data, "Bloodthirst timer expired", stopMaceDespawnSequence)
        }

        state.looseMaces.values.forEach { loose ->
            val timerEnd = loose.timerEndEpochSeconds ?: return@forEach
            if (timerEnd <= now) {
                manualDespawn(loose.maceUuid)
            }
        }
    }

    fun manualDespawnMace(maceUuid: UUID, stopMaceDespawnSequence: (UUID) -> Unit) {
        val loose = state.looseMaces[maceUuid] ?: return
        stopMaceDespawnSequence(maceUuid)
        state.looseMaces.remove(maceUuid)

        chunkControl.setChunkForceState(loose.location, false)

        purgeDroppedMaceEntities(maceUuid, listOfNotNull(loose.location.world))

        messaging.broadcast(announcements.maceLost(loose.location))
        saveData()
        logVerboseEvent(
            "DESPAWN",
            null,
            null,
            maceUuid,
            loose.location,
            null,
            "DESPAWN",
            null,
            emptyMap(),
            null,
            null
        )
    }

    fun maceAbandonsPlayer(
        player: Player?,
        playerUuid: UUID,
        data: MaceWielder,
        reason: String,
        stopMaceDespawnSequence: (UUID) -> Unit
    ) {
        val wielderName = player?.name ?: Bukkit.getOfflinePlayer(playerUuid).name ?: playerUuid.toString()

        // Send personal message to the player immediately (only if online)
        player?.let {
            stopWielderEffects(it)
            sendBreakupVoiceline(it)
            it.sendMessage(messageService.getLegacy("lifecycle.abandoned-personal"))
        }

        // Announce to all players that a mace was abandoned
        messaging.broadcastAnnouncement(messaging.announcementMaceAbandoned(wielderName))

        // #7: Before abandoning, save total hold time
        val currentSessionDuration = if (data.currentHoldSessionStartEpoch != null) {
            val sessionSeconds = (currentTime() - data.currentHoldSessionStartEpoch!!).toLong()
            sessionSeconds / 60
        } else {
            0L
        }
        data.totalHoldTimeMinutes += currentSessionDuration
        data.currentHoldSessionStartEpoch = null

        // Schedule the actual removal after 3 seconds (#9: Handle disconnects properly)
        scheduler.runGlobalLater(60) { // 60 ticks = 3 seconds
            // #9: If player disconnected and we're removing the mace, add them to pending removal
            if (player == null || !player.isOnline) {
                state.pendingMaceRemoval.add(playerUuid)
            }

            // Remove mace from online player's inventory
            player?.let { onlinePlayer ->
                if (onlinePlayer.isOnline) {
                    onlinePlayer.inventory.forEach { item ->
                        if (item != null && isRealMace(item)) {
                            onlinePlayer.inventory.remove(item)
                            return@forEach
                        }
                    }
                    if (isRealMace(onlinePlayer.itemOnCursor)) {
                        onlinePlayer.setItemOnCursor(null)
                    }
                }
            }

            state.maceWielders.remove(playerUuid)
            val trackedLoose = state.looseMaces[data.maceUuid]
            trackedLoose?.let { loose ->
                stopMaceDespawnSequence(data.maceUuid)
                chunkControl.setChunkForceState(loose.location, false)
                state.looseMaces.remove(data.maceUuid)
            }

            val purgeWorlds = buildList {
                player?.world?.let(::add)
                trackedLoose?.location?.world?.let(::add)
            }
            purgeDroppedMaceEntities(data.maceUuid, purgeWorlds)

            saveData()

            messaging.broadcast(announcements.bloodthirstUnmet(wielderName, reason))

            logVerboseEvent(
                "ABANDON",
                wielderName,
                playerUuid,
                data.maceUuid,
                null,
                null,
                "ABANDONED",
                reason,
                mapOf(
                    "hold_time_minutes" to data.totalHoldTimeMinutes
                ),
                null,
                null
            )
        }
    }

    fun purgeDroppedMaceEntities(maceUuid: UUID, candidateWorlds: Collection<World>? = null) {
        val worlds = candidateWorlds?.filterNotNull()?.takeIf { it.isNotEmpty() } ?: Bukkit.getWorlds()
        worlds.forEach { world ->
            world.getEntitiesByClass(Item::class.java)
                .filter { items.getMaceUuid(it.itemStack) == maceUuid }
                .forEach(Item::remove)
        }
    }

    private fun sendBreakupVoiceline(player: Player) {
        if (!player.isOnline || breakupVoicelines.isEmpty()) return
        val line = breakupVoicelines[Random.nextInt(breakupVoicelines.size)]
        player.sendMessage(messageService.getLegacy("lifecycle.breakup-format", "line" to line))
    }
}

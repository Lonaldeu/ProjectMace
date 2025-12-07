package me.lonaldeu.projectmace.mace.admin

import me.lonaldeu.projectmace.mace.core.MaceItems
import me.lonaldeu.projectmace.mace.core.MaceMessaging
import me.lonaldeu.projectmace.mace.domain.MaceEffects
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import me.lonaldeu.projectmace.mace.domain.MaceState
import me.lonaldeu.projectmace.mace.tasks.MaceDespawnTasks
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import java.util.UUID

internal class AdminUnclaim(
    private val state: MaceState,
    private val effects: MaceEffects,
    private val lifecycle: MaceLifecycle,
    private val items: MaceItems,
    private val messaging: MaceMessaging,
    private val despawnTasks: MaceDespawnTasks,
    private val saveData: () -> Unit,
    private val flushDataIfDirty: (Boolean) -> Unit,
    private val logVerboseEvent: (
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
    ) -> Unit,
    private val resolveOfflinePlayer: (String) -> OfflinePlayer?
) {

    fun handle(sender: CommandSender, args: List<String>): Boolean {
        if (args.isEmpty()) {
            messaging.sendLegacyMessage(sender, "&cUsage: /mace unclaim <player|all>")
            return true
        }

        val targetArg = args.first()
        return if (targetArg.equals("all", ignoreCase = true)) {
            unclaimAll(sender)
        } else {
            unclaimSingle(sender, targetArg)
        }
    }

    private fun unclaimAll(sender: CommandSender): Boolean {
        val wieldersCleared = state.maceWielders.size
        val looseCleared = state.looseMaces.size

        state.maceWielders.keys.mapNotNull(Bukkit::getPlayer).forEach { player ->
            effects.stopWielderEffects(player)
            items.removeTaggedMaces(player, lifecycle::isRealMace)
        }

        state.looseMaces.values.forEach { loose ->
            despawnTasks.stopMaceDespawnSequence(loose.maceUuid)
            despawnTasks.releaseChunk(loose.location)
        }

        state.maceWielders.clear()
        state.looseMaces.clear()
        state.pendingMaceRemoval.clear()

        saveData()
        flushDataIfDirty(true)

        messaging.sendLegacyMessage(sender, "&aAll legendary Maces have been unclaimed and reset.")
        messaging.broadcastAnnouncement(messaging.announcementDivineInterventionAll())

        logVerboseEvent(
            "UNCLAIM",
            sender.name,
            null,
            null,
            null,
            null,
            "ALL_CLEARED",
            null,
            mapOf(
                "wielders_cleared" to wieldersCleared,
                "loose_cleared" to looseCleared
            ),
            null,
            null
        )
        return true
    }

    private fun unclaimSingle(sender: CommandSender, targetArg: String): Boolean {
        val target = resolveOfflinePlayer(targetArg)
        if (target == null) {
            messaging.sendLegacyMessage(sender, "&cPlayer '$targetArg' not found.")
            return true
        }

        val targetUuid = target.uniqueId
        val data = state.maceWielders[targetUuid]
        if (data == null) {
            messaging.sendLegacyMessage(sender, "&c'${target.name ?: targetArg}' does not currently wield a Legendary Mace.")
            return true
        }

        val online = target.player?.takeIf { it.isOnline }
        if (online != null) {
            effects.stopWielderEffects(online)
            items.removeTaggedMaces(online, lifecycle::isRealMace)
        } else {
            state.pendingMaceRemoval.add(targetUuid)
        }

        state.maceWielders.remove(targetUuid)
        saveData()
        flushDataIfDirty(false)

        val displayName = target.name ?: targetArg
        messaging.sendLegacyMessage(sender, "&aThe Legendary Mace has been unclaimed from $displayName.")
        messaging.broadcastAnnouncement(messaging.announcementDivineInterventionSingle(displayName))

        logVerboseEvent(
            "UNCLAIM",
            sender.name,
            null,
            null,
            null,
            null,
            null,
            "Admin unclaimed mace from a specific player.",
            mapOf(
                "target_name" to displayName,
                "target_uuid" to targetUuid.toString()
            ),
            null,
            null
        )
        return true
    }
}

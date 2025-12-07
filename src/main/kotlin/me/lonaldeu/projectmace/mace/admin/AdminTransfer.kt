package me.lonaldeu.projectmace.mace.admin

import me.lonaldeu.projectmace.mace.core.MaceItems
import me.lonaldeu.projectmace.mace.core.MaceMessaging
import me.lonaldeu.projectmace.mace.domain.MaceEffects
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import me.lonaldeu.projectmace.mace.domain.MaceState
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID

internal class AdminTransfer(
    private val state: MaceState,
    private val lifecycle: MaceLifecycle,
    private val effects: MaceEffects,
    private val items: MaceItems,
    private val messaging: MaceMessaging,
    private val saveData: () -> Unit,
    private val flushDataIfDirty: () -> Unit,
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

    fun handle(sender: CommandSender, currentOwnerName: String?, newOwnerName: String?): Boolean {
        if (currentOwnerName.isNullOrBlank() || newOwnerName.isNullOrBlank()) {
            messaging.sendLegacyMessage(sender, "&cUsage: /mace transfer <current owner> <new owner>")
            return true
        }

        val newOwner = Bukkit.getPlayerExact(newOwnerName)
        if (newOwner == null) {
            messaging.sendLegacyMessage(sender, "&cNew owner '$newOwnerName' is not online or does not exist.")
            return true
        }

        val currentOwner = resolveOfflinePlayer(currentOwnerName)
        if (currentOwner == null) {
            messaging.sendLegacyMessage(sender, "&cPlayer '$currentOwnerName' not found.")
            return true
        }

        val currentUuid = currentOwner.uniqueId
        val currentData = state.maceWielders[currentUuid]
        if (currentData == null) {
            messaging.sendLegacyMessage(sender, "&c'${currentOwner.name ?: currentOwnerName}' is not a current wielder of a Legendary Mace.")
            return true
        }

        if (state.maceWielders.containsKey(newOwner.uniqueId)) {
            messaging.sendLegacyMessage(sender, "&c'${newOwner.name}' is already a wielder and cannot receive a Mace.")
            return true
        }

        val newMaceUuid = UUID.randomUUID()
        items.giveTaggedMace(newOwner, newMaceUuid)

        state.maceWielders.remove(currentUuid)

        lifecycle.registerWielder(newOwner, newMaceUuid, currentData.timerEndEpochSeconds)

        val currentOnline = currentOwner.player?.takeIf { it.isOnline }
        if (currentOnline != null) {
            effects.stopWielderEffects(currentOnline)
            items.removeTaggedMaces(currentOnline, lifecycle::isRealMace)
            state.pendingMaceRemoval.remove(currentUuid)
        } else {
            state.pendingMaceRemoval.add(currentUuid)
        }

        effects.startWielderEffects(newOwner)

        saveData()
        flushDataIfDirty()

        val currentName = currentOwner.name ?: currentOwnerName
        messaging.sendLegacyMessage(sender, "&aSuccessfully transferred the Legendary Mace from $currentName to ${newOwner.name}.")
        currentOwner.player?.takeIf { it.isOnline }?.let { player ->
            messaging.sendLegacyMessage(player, "&cAn admin has transferred your Legendary Mace to ${newOwner.name}.")
        }
        messaging.sendLegacyMessage(newOwner, "&aAn admin has transferred a Legendary Mace to you from $currentName.")

        logVerboseEvent(
            "TRANSFER_ADMIN",
            sender.name,
            null,
            null,
            null,
            null,
            null,
            "Admin transferred mace.",
            mapOf(
                "from_name" to currentName,
                "from_uuid" to currentUuid.toString(),
                "to_name" to newOwner.name,
                "to_uuid" to newOwner.uniqueId.toString(),
                "new_mace_uuid" to newMaceUuid.toString(),
                "timer_end" to currentData.timerEndEpochSeconds
            ),
            null,
            null
        )
        return true
    }
}

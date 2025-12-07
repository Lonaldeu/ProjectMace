package me.lonaldeu.projectmace.mace.admin

import me.lonaldeu.projectmace.mace.domain.model.MaceConstants
import me.lonaldeu.projectmace.mace.core.MaceItems
import me.lonaldeu.projectmace.mace.core.MaceMessaging
import me.lonaldeu.projectmace.mace.core.nowSeconds
import me.lonaldeu.projectmace.mace.domain.MaceEffects
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import me.lonaldeu.projectmace.mace.domain.MaceState
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.util.UUID

internal class AdminRefund(
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
    private val maxLegendaryMaces: () -> Int,
    private val bloodthirstDurationSeconds: () -> Long,
    private val nowSeconds: () -> Double = ::nowSeconds
) {

    fun handle(sender: CommandSender, targetName: String?): Boolean {
        if (targetName.isNullOrBlank()) {
            messaging.sendLegacyMessage(sender, "&cUsage: /mace refund <player>")
            return true
        }

        val target = Bukkit.getPlayerExact(targetName)
        if (target == null) {
            messaging.sendLegacyMessage(sender, "&cPlayer '$targetName' must be online to receive a refund.")
            return true
        }

        if (state.maceWielders.containsKey(target.uniqueId) || lifecycle.playerCarriesLegendaryMace(target)) {
            messaging.sendLegacyMessage(sender, "&c${target.name} already possesses a Legendary Mace.")
            return true
        }

        if (state.maceCount() >= maxLegendaryMaces()) {
            messaging.sendLegacyMessage(sender, "&cThe server is already at the Legendary Mace limit. Unclaim or despawn a Mace first.")
            return true
        }

        val maceUuid = UUID.randomUUID()
        items.giveTaggedMace(target, maceUuid)

        val timerEnd = nowSeconds() + bloodthirstDurationSeconds()
        lifecycle.registerWielder(target, maceUuid, timerEnd)
        state.pendingMaceRemoval.remove(target.uniqueId)
        effects.startWielderEffects(target)

        saveData()
        flushDataIfDirty()

        messaging.sendLegacyMessage(sender, "&aRefunded a Legendary Mace to ${target.name}.")
        messaging.sendLegacyMessage(target, "&aAn admin has refunded your Legendary Mace. The bloodthirst timer has been reset.")

        logVerboseEvent(
            "REFUND",
            sender.name,
            target.uniqueId,
            maceUuid,
            target.location,
            null,
            "REFUND_GRANTED",
            "Admin refund command executed.",
            mapOf(
                "recipient_name" to target.name,
                "recipient_uuid" to target.uniqueId.toString(),
                "timer_end" to timerEnd
            ),
            timerEnd,
            null
        )

        return true
    }
}

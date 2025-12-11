package me.lonaldeu.projectmace.mace.events

import me.lonaldeu.projectmace.mace.core.MaceItems
import me.lonaldeu.projectmace.mace.core.MaceMessaging
import me.lonaldeu.projectmace.mace.core.nowSeconds
import me.lonaldeu.projectmace.mace.domain.MaceEffects
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import me.lonaldeu.projectmace.mace.domain.MaceState
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

import me.lonaldeu.projectmace.mace.core.MaceContext

internal class PlayerLifecycleEvents(
    private val context: MaceContext
) : Listener {

    private val state get() = context.state
    private val lifecycle get() = context.lifecycle
    private val effects get() = context.effects
    private val items get() = context.items
    private val messaging get() = context.messaging
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

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        state.maceWielders[player.uniqueId]?.let { data ->
            if (data.timerEndEpochSeconds > nowSeconds()) {
                effects.startWielderEffects(player)
            }
        }

        if (state.pendingMaceRemoval.remove(player.uniqueId)) {
            items.removeTaggedMaces(player, lifecycle::isRealMace)
            messaging.sendLegacyMessage(player, "&cYour Legendary Mace was transferred away while you were offline.")
            saveData()
            logVerboseEvent(
                "PENDING_REMOVAL",
                player.name,
                player.uniqueId,
                null,
                null,
                null,
                "CLEARED_ON_LOGIN",
                null,
                emptyMap(),
                null,
                null
            )
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        effects.stopWielderEffects(player)
    }
}

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

internal class PlayerLifecycleEvents(
    private val state: MaceState,
    private val lifecycle: MaceLifecycle,
    private val effects: MaceEffects,
    private val items: MaceItems,
    private val messaging: MaceMessaging,
    private val saveData: () -> Unit,
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
    private val nowSeconds: () -> Double = ::nowSeconds
) : Listener {

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

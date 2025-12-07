package me.lonaldeu.projectmace.mace.domain

import me.lonaldeu.projectmace.mace.core.MaceMessaging
import me.lonaldeu.projectmace.platform.PlatformScheduler
import org.bukkit.command.CommandSender

internal class MaceSearchBridge(
    scheduler: PlatformScheduler,
    private val state: MaceState,
    isFolia: Boolean,
    maxLegendaryMaces: () -> Int,
    lifecycle: MaceLifecycle,
    private val messaging: MaceMessaging,
    private val logVerboseEvent: (
        eventType: String,
        playerName: String?,
        playerUuid: java.util.UUID?,
        maceUuid: java.util.UUID?,
        location: org.bukkit.Location?,
        containerContext: String?,
        outcome: String?,
        reason: String?,
        additionalContext: Map<String, Any?>,
        timerEnd: Double?,
        timeLeft: Double?
    ) -> Unit
) {

    val searchService: MaceSearchService = MaceSearchService(
        scheduler = scheduler,
        looseMaces = state.looseMaces,
        isFolia = isFolia,
        maxLegendaryMaces = maxLegendaryMaces,
        isRealMace = lifecycle::isRealMace,
        dispatchToSender = ::dispatchToSender,
        sendLegacyMessage = messaging::sendLegacyMessage,
        logSearch = { sender, realLocations, illegalLocations ->
            logVerboseEvent(
                "SEARCH",
                sender.name,
                null,
                null,
                null,
                null,
                null,
                "Admin initiated a server-wide mace search.",
                mapOf(
                    "real_found" to realLocations.size,
                    "illegal_found" to illegalLocations.size,
                    "real_locations" to realLocations,
                    "illegal_locations" to illegalLocations
                ),
                null,
                null
            )
        }
    )

    private fun dispatchToSender(sender: CommandSender, action: (CommandSender) -> Unit) {
        messaging.dispatchToSender(sender, action)
    }
}

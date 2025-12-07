package me.lonaldeu.projectmace.mace.core

import me.lonaldeu.projectmace.config.MessageService
import net.kyori.adventure.text.Component
import org.bukkit.Location

/**
 * Centralises narrative broadcast components for Legendary Mace events.
 */
class LegendaryMaceAnnouncements(
    private val messageService: MessageService
) {

    fun maceLost(location: Location): Component {
        val worldName = location.world?.name ?: "unknown"
        return messageService.getMiniMessage(
            "announcements.mace-lost",
            "x" to location.blockX.toString(),
            "y" to location.blockY.toString(),
            "z" to location.blockZ.toString(),
            "world" to worldName
        )
    }

    fun bloodthirstUnmet(wielderName: String, reason: String): Component =
        messageService.getMiniMessage(
            "announcements.bloodthirst-unmet",
            "wielder" to wielderName,
            "reason" to reason
        )

    fun divineInterventionAll(): Component =
        messageService.getMiniMessage("announcements.divine-intervention-all")

    fun divineInterventionSingle(displayName: String): Component =
        messageService.getMiniMessage(
            "announcements.divine-intervention-single",
            "wielder" to displayName
        )

    fun relicForged(wielderName: String): Component =
        messageService.getMiniMessage(
            "announcements.relic-forged",
            "wielder" to wielderName
        )

    fun maceLoosened(playerName: String): Component =
        messageService.getMiniMessage(
            "announcements.mace-loosened",
            "player" to playerName
        )

    fun voidRecovery(): Component =
        messageService.getMiniMessage("announcements.void-recovery")

    fun maceAbandoned(wielderName: String): Component {
        return messageService.getMiniMessage(
            "announcements.mace-abandoned",
            "wielder" to wielderName
        )
    }
}

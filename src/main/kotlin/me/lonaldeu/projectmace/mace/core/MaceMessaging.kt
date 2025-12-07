package me.lonaldeu.projectmace.mace.core

import me.lonaldeu.projectmace.platform.PlatformScheduler
import me.lonaldeu.projectmace.mace.core.LegendaryMaceAnnouncements
import me.lonaldeu.projectmace.mace.domain.model.MaceConstants
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.BlockCommandSender
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class MaceMessaging(
    private val scheduler: PlatformScheduler,
    private val legacySerializer: LegacyComponentSerializer,
    private val miniMessage: MiniMessage,
    private val announcements: LegendaryMaceAnnouncements,
    private val announcementCooldownMillis: Long,
    private val lastAnnouncementTimes: MutableMap<String, Long> = mutableMapOf()
) {

    fun dispatchToSender(sender: CommandSender, action: (CommandSender) -> Unit) {
        if (!scheduler.isFolia()) {
            scheduler.runSync { action(sender) }
            return
        }

        when (sender) {
            is Player -> scheduler.runAtEntity(sender) { action(sender) }
            is BlockCommandSender -> {
                val block = sender.block
                scheduler.runAtBlock(block.world, block.x, block.z) { action(sender) }
            }
            else -> scheduler.runSync { action(sender) }
        }
    }

    fun sendLegacyMessage(sender: CommandSender, message: String) {
        val component: Component = legacySerializer.deserialize(message)
        sender.sendMessage(component)
    }

    fun sendLegacyMessage(player: Player, message: String) {
        sendLegacyMessage(player as CommandSender, message)
    }

    fun broadcast(component: Component) {
        // Schedule on main thread to ensure thread safety, then broadcast
        scheduler.runSync {
            Bukkit.getOnlinePlayers().forEach { player ->
                player.sendMessage(component)
            }
        }
    }

    fun broadcastAnnouncement(component: Component) {
        broadcast(component)
    }

    /**
     * Broadcast with rate limiting (#3)
     * Checks if announcement of this type has been sent recently
     * Returns true if announcement was sent, false if rate-limited
     */
    fun broadcastAnnouncementWithRateLimit(announcementType: String, component: Component): Boolean {
        val now = System.currentTimeMillis()
        val lastTime = lastAnnouncementTimes[announcementType] ?: 0L
        val timeSinceLastAnnouncement = now - lastTime

        // If cooldown period hasn't passed, skip this announcement
        if (timeSinceLastAnnouncement < announcementCooldownMillis) {
            return false
        }

        lastAnnouncementTimes[announcementType] = now
        broadcast(component)
        return true
    }

    fun announcementMaceLost(location: Location): Component = announcements.maceLost(location)
    fun announcementBloodthirstUnmet(wielderName: String, reason: String): Component =
        announcements.bloodthirstUnmet(wielderName, reason)

    fun announcementDivineInterventionAll(): Component = announcements.divineInterventionAll()
    fun announcementDivineInterventionSingle(displayName: String): Component =
        announcements.divineInterventionSingle(displayName)

    fun announcementRelicForged(wielderName: String): Component = announcements.relicForged(wielderName)
    fun announcementMaceLoosened(playerName: String): Component = announcements.maceLoosened(playerName)
    fun announcementVoidRecovery(): Component = announcements.voidRecovery()
    fun announcementMaceAbandoned(wielderName: String): Component = 
        announcements.maceAbandoned(wielderName)

    fun miniMessage(): MiniMessage = miniMessage
}

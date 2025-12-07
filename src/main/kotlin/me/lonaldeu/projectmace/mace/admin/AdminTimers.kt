package me.lonaldeu.projectmace.mace.admin

import me.lonaldeu.projectmace.mace.core.MaceMessaging
import me.lonaldeu.projectmace.mace.core.nowSeconds
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import me.lonaldeu.projectmace.mace.domain.MaceState
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import java.util.Locale
import java.util.UUID

internal class AdminTimers(
    private val state: MaceState,
    private val lifecycle: MaceLifecycle,
    private val messaging: MaceMessaging,
    private val saveData: () -> Unit,
    private val flushDataIfDirty: () -> Unit,
    private val resolveOfflinePlayer: (String) -> OfflinePlayer?,
    private val resolveDisplayName: (OfflinePlayer) -> String,
    private val formatDuration: (Long) -> String,
    private val maxLegendaryMaces: () -> Int,
    private val bloodthirstDurationSeconds: () -> Long,
    private val nowSeconds: () -> Double = ::nowSeconds
) {

    fun handleTimerCommand(sender: CommandSender, args: List<String>): Boolean {
        val limit = maxLegendaryMaces()
        val currentCount = state.maceCount()
        val craftable = (limit - currentCount).coerceAtLeast(0)
        val craftableColor = if (craftable > 0) "&a" else "&c"

        if (args.isEmpty()) {
            messaging.sendLegacyMessage(
                sender,
                "&6[Mace] &aLegendary Maces&7: &e$currentCount&7/&e$limit &7(${craftableColor}$craftable&7 craftable remaining)"
            )
            if (state.maceWielders.isEmpty()) {
                messaging.sendLegacyMessage(sender, "&6[Mace] &eNo current wielders.")
                return true
            }
            messaging.sendLegacyMessage(sender, "&6[Mace] &aCurrent wielder timers:")
            val now = nowSeconds()
            val entries = state.maceWielders.map { (uuid, data) ->
                val secondsLeft = kotlin.math.max(0.0, data.timerEndEpochSeconds - now).toLong()
                resolveDisplayName(Bukkit.getOfflinePlayer(uuid)) to secondsLeft
            }.sortedByDescending { it.second }

            entries.forEach { (name, seconds) ->
                messaging.sendLegacyMessage(sender, "&7- &b$name&7: &a${formatDuration(seconds)}")
            }
            return true
        }

        messaging.sendLegacyMessage(
            sender,
            "&6[Mace] &aLegendary Maces&7: &e$currentCount&7/&e$limit &7(${craftableColor}$craftable&7 craftable remaining)"
        )

        val targetName = args.first()
        val target = resolveOfflinePlayer(targetName)
        if (target == null) {
            messaging.sendLegacyMessage(sender, "&cPlayer '$targetName' not found.")
            return true
        }

        val data = state.maceWielders[target.uniqueId]
        if (data == null) {
            messaging.sendLegacyMessage(sender, "&e${target.name ?: targetName} &7is not a current wielder.")
            return true
        }

        val secondsLeft = kotlin.math.max(0.0, data.timerEndEpochSeconds - nowSeconds()).toLong()
        messaging.sendLegacyMessage(sender, "&6[Mace] &b${target.name ?: targetName}&7: &a${formatDuration(secondsLeft)}")
        return true
    }

    fun handleBloodTimerCommand(sender: CommandSender, args: List<String>): Boolean {
        if (args.size < 2) {
            messaging.sendLegacyMessage(sender, "&cUsage: /mace bloodtimer <player> <add|remove|reset> [seconds]")
            return true
        }

        val targetName = args[0]
        val action = args[1].lowercase(Locale.US)
        val target = resolveOfflinePlayer(targetName)
        if (target == null) {
            messaging.sendLegacyMessage(sender, "&cPlayer '$targetName' not found.")
            return true
        }

        val data = state.maceWielders[target.uniqueId]
        if (data == null) {
            messaging.sendLegacyMessage(sender, "&e${target.name ?: targetName} &7is not a current wielder.")
            return true
        }

        val now = nowSeconds()
        val currentEnd = data.timerEndEpochSeconds

        when (action) {
            "add", "remove" -> {
                if (args.size < 3) {
                    messaging.sendLegacyMessage(sender, "&cUsage: /mace bloodtimer <player> <add|remove> <seconds>")
                    return true
                }
                val seconds = args[2].toLongOrNull()
                if (seconds == null || seconds <= 0) {
                    messaging.sendLegacyMessage(sender, "&cSeconds must be a positive whole number.")
                    return true
                }
                val adjustment = if (action == "add") seconds else -seconds
                data.timerEndEpochSeconds = currentEnd + adjustment
                saveData()
                flushDataIfDirty()

                if (action == "add") {
                    messaging.sendLegacyMessage(sender, "&aAdded &e${seconds}s &ato ${target.name ?: targetName}'s blood timer.")
                    target.player?.takeIf { it.isOnline }?.let { player ->
                        messaging.sendLegacyMessage(player, "&aYour Mace blood timer was increased by &e${seconds}s&a.")
                    }
                } else {
                    messaging.sendLegacyMessage(sender, "&aRemoved &e${seconds}s &afrom ${target.name ?: targetName}'s blood timer.")
                    target.player?.takeIf { it.isOnline }?.let { player ->
                        messaging.sendLegacyMessage(player, "&cYour Mace blood timer was reduced by &e${seconds}s&c.")
                    }
                }
                return true
            }

            "reset" -> {
                data.timerEndEpochSeconds = now + bloodthirstDurationSeconds()
                data.lastChance = false
                saveData()
                flushDataIfDirty()

                messaging.sendLegacyMessage(sender, "&aReset ${target.name ?: targetName}'s blood timer to 24h.")
                target.player?.takeIf { it.isOnline }?.let { player ->
                    messaging.sendLegacyMessage(player, "&aYour Mace blood timer was reset to 24h.")
                }
                return true
            }
        }

        messaging.sendLegacyMessage(sender, "&cUsage: /mace bloodtimer <player> <add|remove|reset> [seconds]")
        return true
    }
}

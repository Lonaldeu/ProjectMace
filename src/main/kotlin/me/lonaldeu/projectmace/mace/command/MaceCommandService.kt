package me.lonaldeu.projectmace.mace.command

import me.lonaldeu.projectmace.license.StringVault

import me.lonaldeu.projectmace.mace.LegendaryMaceManager
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale
import java.util.UUID

class MaceCommandService(
    private val plugin: JavaPlugin,
    private val manager: LegendaryMaceManager?,
    private val onReload: () -> Unit
) : BasicCommand {

    private val legacy = LegacyComponentSerializer.legacyAmpersand()
    private val subcommands = listOf("help", "unclaim", "search", "transfer", "timer", "bloodtimer", "refund", "reload")

    fun register() {
        plugin.registerCommand("mace", this)
    }

    override fun execute(stack: CommandSourceStack, args: Array<String>) {
        val sender = stack.sender

        // Restricted mode check
        if (manager == null) {
            if (args.isNotEmpty() && args[0].lowercase(Locale.US) == "reload") {
                if (!ensurePermission(sender, "mace.reload")) return
                onReload()
                sender.sendMessage(legacy.deserialize("&aConfiguration reloaded successfully!"))
                return
            }
            sender.sendMessage(legacy.deserialize("&cLicense validation failed! Plugin functionality is disabled."))
            sender.sendMessage(legacy.deserialize("&cPlease check config.yml and run &e/mace reload"))
            return
        }

        if (args.isEmpty()) {
            if (!ensurePermission(sender, "mace.help")) return
            sendUsage(sender)
            return
        }

        when (args[0].lowercase(Locale.US)) {
            "help" -> {
                if (!ensurePermission(sender, "mace.help")) return
                sendHelp(sender)
            }
            "unclaim" -> {
                if (!ensurePermission(sender, StringVault.get("PERM_UNCLAIM"))) return
                manager!!.handleUnclaimCommand(sender, args.drop(1))
            }
            "search" -> {
                if (!ensurePermission(sender, StringVault.get("PERM_SEARCH"))) return
                manager!!.handleSearchCommand(sender)
            }
            "transfer" -> {
                if (!ensurePermission(sender, "mace.transfer")) return
                manager!!.handleTransferCommand(sender, args.getOrNull(1), args.getOrNull(2))
            }
            "timer" -> {
                if (!ensurePermission(sender, "mace.timer")) return
                manager!!.handleTimerCommand(sender, args.drop(1))
            }
            "bloodtimer" -> {
                if (!ensurePermission(sender, "mace.bloodtimer")) return
                manager!!.handleBloodTimerCommand(sender, args.drop(1))
            }
            "refund" -> {
                if (!ensurePermission(sender, "mace.refund")) return
                manager!!.handleRefundCommand(sender, args.getOrNull(1))
            }
            "reload" -> {
                if (!ensurePermission(sender, "mace.reload")) return
                onReload()
                sender.sendMessage(legacy.deserialize("&aConfiguration reloaded successfully!"))
            }
            else -> {
                if (!ensurePermission(sender, "mace.help")) return
                sendUsage(sender)
            }
        }
    }

    override fun suggest(stack: CommandSourceStack, args: Array<String>): Collection<String> {
        val sender = stack.sender

        if (args.isEmpty()) {
            return subcommands.filter { sender.hasPermission("mace.$it") }
        }

        val lowerSub = args[0].lowercase(Locale.US)

        // Filter subcommand suggestions based on permission
        if (args.size == 1) {
            return subcommands.filter { 
                it.startsWith(lowerSub) && sender.hasPermission("mace.$it") 
            }
        }

        // Check permission for the specific subcommand being used
        if (!sender.hasPermission("mace.$lowerSub")) {
            return emptyList()
        }

        val suggestions = when (args.size) {
            2 -> when (lowerSub) {
                "unclaim" -> listOf("all") + knownWielderNames()
                "transfer" -> knownWielderNames()
                "timer" -> (knownWielderNames() + onlinePlayerNames()).distinct()
                "bloodtimer" -> knownWielderNames()
                "refund" -> onlinePlayerNames()
                else -> emptyList()
            }
            3 -> when (lowerSub) {
                "transfer" -> onlinePlayerNames()
                "bloodtimer" -> listOf("add", "remove", "reset")
                else -> emptyList()
            }
            4 -> when (lowerSub) {
                "bloodtimer" -> {
                    val action = args.getOrNull(2)?.lowercase(Locale.US)
                    if (action == "add" || action == "remove") listOf("60", "300", "3600", "86400") else emptyList()
                }
                else -> emptyList()
            }
            else -> emptyList()
        }

        val currentArg = args.last().lowercase(Locale.US)
        return suggestions.filter { it.lowercase(Locale.US).startsWith(currentArg) }.distinct()
    }

    override fun canUse(sender: CommandSender): Boolean {
        // Allow if sender has any mace permission
        return sender.hasPermission("mace.*") || 
               sender.hasPermission("mace.help") ||
               sender.hasPermission("mace.unclaim") ||
               sender.hasPermission("mace.search") ||
               sender.hasPermission("mace.transfer") ||
               sender.hasPermission("mace.timer") ||
               sender.hasPermission("mace.bloodtimer") ||
               sender.hasPermission("mace.refund") ||
               sender.hasPermission("mace.reload")
    }

    override fun permission(): String = "mace.*"

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage(legacy.deserialize("&cUsage: /mace <${subcommands.joinToString("|")}>"))
        sender.sendMessage(legacy.deserialize("&7Try &e/mace help &7for details."))
    }

    private fun sendHelp(sender: CommandSender) {
        val lines = listOf(
            "&6/mace unclaim <player|all> &7- Force unclaim a Legendary Mace.",
            "&6/mace search &7- Scan the server for legendary or illegal maces.",
            "&6/mace transfer <from> <to> &7- Move a mace between players.",
            "&6/mace timer [player] &7- View bloodthirst timers.",
            "&6/mace bloodtimer <player> <add|remove|reset> [seconds] &7- Adjust a bloodthirst timer.",
            "&6/mace reload &7- Reload the configuration."
        )
        sender.sendMessage(legacy.deserialize("&6Legendary Mace Administration:"))
        lines.forEach { line -> sender.sendMessage(legacy.deserialize(line)) }
    }

    private fun knownWielderNames(): List<String> = manager?.getWielderUuids()
        ?.map(::resolveName)
        ?.distinct() ?: emptyList()

    private fun onlinePlayerNames(): List<String> = Bukkit.getOnlinePlayers().map(Player::getName)

    private fun resolveName(uuid: UUID): String {
        val offline = Bukkit.getOfflinePlayer(uuid)
        return offline.name ?: uuid.toString()
    }

    private fun ensurePermission(sender: CommandSender, permission: String): Boolean {
        // Check perm - if StringVault is broken, get() returns garbage, ensuring security by failing this check unless user actually has "garbage" permission
        if (sender.hasPermission(permission)) {
            return true
        }
        sender.sendMessage(legacy.deserialize("&cYou do not have permission to use this command."))
        return false
    }
}

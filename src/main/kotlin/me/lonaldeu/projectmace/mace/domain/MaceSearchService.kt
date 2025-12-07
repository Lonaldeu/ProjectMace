package me.lonaldeu.projectmace.mace.domain

import me.lonaldeu.projectmace.mace.domain.model.LooseMace
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.command.CommandSender
import org.bukkit.entity.Item
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.BundleMeta
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

internal class MaceSearchService(
    private val scheduler: me.lonaldeu.projectmace.platform.PlatformScheduler,
    private val looseMaces: MutableMap<UUID, LooseMace>,
    private val isFolia: Boolean,
    private val maxLegendaryMaces: () -> Int,
    private val isRealMace: (ItemStack?) -> Boolean,
    private val dispatchToSender: (CommandSender, (CommandSender) -> Unit) -> Unit,
    private val sendLegacyMessage: (CommandSender, String) -> Unit,
    private val logSearch: (CommandSender, List<String>, List<String>) -> Unit
) {

    private val allowedMaceInventories = setOf(
        InventoryType.CRAFTING,
        InventoryType.PLAYER,
        InventoryType.ANVIL,
        InventoryType.ENCHANTING,
        InventoryType.GRINDSTONE
    )

    fun handleSearchCommand(sender: CommandSender) {
        if (isFolia) {
            dispatchToSender(sender) {
                sendLegacyMessage(it, "&6[Mace] &fScanning server for Legendary Maces...")
            }
            performAdminMaceSearchFolia(sender)
            return
        }

        val results = performAdminMaceSearchSync()
        deliverSearchResults(sender, results)
        logSearch(sender, results.realLocations, results.illegalLocations)
    }

    private fun performAdminMaceSearchSync(): MaceSearchResults {
        val realLocations = mutableListOf<String>()
        val illegalLocations = mutableListOf<String>()

        Bukkit.getOnlinePlayers().forEach { player ->
            checkInventoryForMaces(
                player.inventory,
                "${player.name}'s inventory",
                InventoryType.PLAYER,
                realLocations,
                illegalLocations
            )

            val enderChest = player.enderChest
            checkInventoryForMaces(
                enderChest,
                "${player.name}'s Ender Chest",
                enderChest.type,
                realLocations,
                illegalLocations
            )
        }

        for (world in Bukkit.getWorlds()) {
            for (entity in world.getEntitiesByClass(Item::class.java)) {
                val stack = entity.itemStack
                if (stack.type != Material.MACE) continue
                val location = entity.location
                val context = "a dropped item at X:${location.blockX} Y:${location.blockY} Z:${location.blockZ} in ${world.name}"
                if (isRealMace(stack)) {
                    realLocations.add(context)
                } else {
                    illegalLocations.add(context)
                }
            }

            for (chunk in world.loadedChunks) {
                for (state in chunk.tileEntities) {
                    val holder = state as? InventoryHolder ?: continue
                    val inventory = holder.inventory
                    val loc = state.location
                    val context = "a ${state.type.name} at X:${loc.blockX} Y:${loc.blockY} Z:${loc.blockZ} in ${world.name}"
                    checkInventoryForMaces(
                        inventory,
                        context,
                        inventory.type,
                        realLocations,
                        illegalLocations
                    )
                }
            }

            for (entity in world.entities) {
                if (entity is org.bukkit.entity.Player || entity is Item) continue
                val holder = entity as? InventoryHolder ?: continue
                val inventory = holder.inventory
                val loc = entity.location
                val context = "a ${entity.type.name} at X:${loc.blockX} Y:${loc.blockY} Z:${loc.blockZ} in ${world.name}"
                checkInventoryForMaces(
                    inventory,
                    context,
                    inventory.type,
                    realLocations,
                    illegalLocations
                )
            }

            looseMaces.values.filter { it.location.world == world }.forEach { loose ->
                val loc = loose.location
                realLocations.add("loose mace record at X:${loc.blockX} Y:${loc.blockY} Z:${loc.blockZ} in ${world.name}")
            }
        }

        return MaceSearchResults(realLocations, illegalLocations)
    }

    private fun performAdminMaceSearchFolia(sender: CommandSender) {
        val realLocations = java.util.Collections.synchronizedList(mutableListOf<String>())
        val illegalLocations = java.util.Collections.synchronizedList(mutableListOf<String>())
        val pending = AtomicInteger(1)

        fun taskDone() {
            if (pending.decrementAndGet() == 0) {
                scheduler.runSync {
                    val finalReal = mutableListOf<String>().apply { addAll(realLocations) }
                    val finalIllegal = mutableListOf<String>().apply { addAll(illegalLocations) }

                    looseMaces.values.forEach { loose ->
                        val loc = loose.location
                        val worldName = loc.world?.name ?: "unknown"
                        finalReal.add("loose mace record at X:${loc.blockX} Y:${loc.blockY} Z:${loc.blockZ} in ${worldName}")
                    }

                    val results = MaceSearchResults(finalReal, finalIllegal)
                    deliverSearchResults(sender, results)
                    logSearch(sender, results.realLocations, results.illegalLocations)
                }
            }
        }

        Bukkit.getOnlinePlayers().forEach { player ->
            pending.incrementAndGet()
            scheduler.runAtEntity(player) {
                try {
                    checkInventoryForMaces(
                        player.inventory,
                        "${player.name}'s inventory",
                        InventoryType.PLAYER,
                        realLocations,
                        illegalLocations
                    )

                    val enderChest = player.enderChest
                    checkInventoryForMaces(
                        enderChest,
                        "${player.name}'s Ender Chest",
                        enderChest.type,
                        realLocations,
                        illegalLocations
                    )
                } finally {
                    taskDone()
                }
            }
        }

        Bukkit.getWorlds().forEach { world ->
            val chunkCoords = world.loadedChunks.map { it.x to it.z }
            chunkCoords.forEach { (chunkX, chunkZ) ->
                pending.incrementAndGet()
                scheduler.runAtBlock(world, chunkX shl 4, chunkZ shl 4) {
                    try {
                        val chunk = world.getChunkAt(chunkX, chunkZ)

                        chunk.tileEntities.forEach tileEntityLoop@{ state ->
                            val holder = state as? InventoryHolder ?: return@tileEntityLoop
                            val inventory = holder.inventory
                            val loc = state.location
                            val context = "a ${state.type.name} at X:${loc.blockX} Y:${loc.blockY} Z:${loc.blockZ} in ${world.name}"
                            checkInventoryForMaces(
                                inventory,
                                context,
                                inventory.type,
                                realLocations,
                                illegalLocations
                            )
                        }

                        chunk.entities.forEach entityLoop@{ entity ->
                            when (entity) {
                                is Item -> {
                                    val stack = entity.itemStack
                                    if (stack.type == Material.MACE) {
                                        val location = entity.location
                                        val context = "a dropped item at X:${location.blockX} Y:${location.blockY} Z:${location.blockZ} in ${world.name}"
                                        if (isRealMace(stack)) {
                                            realLocations.add(context)
                                        } else {
                                            illegalLocations.add(context)
                                        }
                                    }
                                }
                                is org.bukkit.entity.Player -> Unit
                                else -> {
                                    val holder = entity as? InventoryHolder ?: return@entityLoop
                                    val inventory = holder.inventory
                                    val loc = entity.location
                                    val context = "a ${entity.type.name} at X:${loc.blockX} Y:${loc.blockY} Z:${loc.blockZ} in ${world.name}"
                                    checkInventoryForMaces(
                                        inventory,
                                        context,
                                        inventory.type,
                                        realLocations,
                                        illegalLocations
                                    )
                                }
                            }
                        }
                    } finally {
                        taskDone()
                    }
                }
            }
        }

        taskDone()
    }

    private fun deliverSearchResults(sender: CommandSender, results: MaceSearchResults) {
        val limit = maxLegendaryMaces()
        dispatchToSender(sender) { target ->
            sendLegacyMessage(target, "&6[Mace] &aSearch complete.")

            if (results.realLocations.isEmpty() && results.illegalLocations.isEmpty()) {
                sendLegacyMessage(target, "&aNo Maces were found on the server.")
                return@dispatchToSender
            }

            if (results.realLocations.isNotEmpty()) {
                if (results.realLocations.size > limit) {
                    sendLegacyMessage(target, "&c&lCRITICAL DUPE ALERT! &cFound more REAL Maces than the max of ${limit}:")
                } else {
                    sendLegacyMessage(target, "&aFound ${results.realLocations.size}/${limit} REAL Mace(s):")
                }
                results.realLocations.forEach { location ->
                    sendLegacyMessage(target, "&a- In $location")
                }
            }

            if (results.illegalLocations.isNotEmpty()) {
                sendLegacyMessage(target, "&cIllegal (duped/untagged) Maces were found:")
                results.illegalLocations.forEach { location ->
                    sendLegacyMessage(target, "&c- In $location")
                }
            }
        }
    }

    private fun checkInventoryForMaces(
        inventory: Inventory?,
        context: String,
        inventoryType: InventoryType?,
        realLocations: MutableList<String>,
        illegalLocations: MutableList<String>
    ) {
        if (inventory == null) return
        inventory.contents.forEach { item ->
            if (item == null || item.type == Material.AIR) return@forEach
            inspectItemStack(item, context, inventoryType, realLocations, illegalLocations)
        }
    }

    private fun inspectItemStack(
        stack: ItemStack,
        context: String,
        inventoryType: InventoryType?,
        realLocations: MutableList<String>,
        illegalLocations: MutableList<String>
    ) {
        val type = stack.type

        if (type == Material.MACE) {
            val target = if (isRealMace(stack) && (inventoryType == null || inventoryType in allowedMaceInventories)) {
                realLocations
            } else {
                illegalLocations
            }
            target.add(context)
            return
        }

        if (type == Material.BUNDLE) {
            val bundleMeta = stack.itemMeta as? BundleMeta ?: return
            bundleMeta.items.forEach { nested ->
                inspectItemStack(nested, "a Bundle in $context", null, realLocations, illegalLocations)
            }
            return
        }

        val blockStateMeta = stack.itemMeta as? BlockStateMeta ?: return
        val shulker = blockStateMeta.blockState as? ShulkerBox ?: return
        checkInventoryForMaces(
            shulker.inventory,
            "a Shulker Box in $context",
            shulker.inventory.type,
            realLocations,
            illegalLocations
        )
    }
}

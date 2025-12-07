package me.lonaldeu.projectmace.mace.domain

import me.lonaldeu.projectmace.config.MessageService
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockDispenseEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.InventoryHolder
import org.bukkit.plugin.Plugin

/**
 * Prevents Legendary Maces from being stored in or moved to restricted inventories.
 */
class MaceInventoryGuard(
    private val plugin: Plugin,
    private val isRealMace: (ItemStack?) -> Boolean,
    private val messageService: MessageService,
    private val blockDecoratedPots: Boolean,
    private val blockItemFrames: Boolean,
    private val blockArmorStands: Boolean,
    private val blockAllays: Boolean
) : Listener {

    private val protectedInventories = setOf(
        InventoryType.CRAFTING,
        InventoryType.PLAYER,
        InventoryType.ANVIL,
        InventoryType.ENCHANTING,
        InventoryType.GRINDSTONE
    )

    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) {
            return
        }
        val player = event.player
        if (!playerIsCarryingMace(player)) {
            return
        }
        val block = event.clickedBlock ?: return
        if (block.type != Material.DECORATED_POT) {
            return
        }
        if (!blockDecoratedPots) {
            return
        }
        event.isCancelled = true
        player.sendMessage(messageService.getLegacy("inventory.decorated-pot"))
        player.updateInventory()
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val rightClicked = event.rightClicked
        val player = event.player
        if (!playerIsCarryingMace(player)) {
            return
        }

        when (rightClicked.type) {
            EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME -> {
                if (blockItemFrames) {
                    event.isCancelled = true
                    player.sendMessage(messageService.getLegacy("inventory.item-frame"))
                    player.updateInventory()
                }
                return
            }
            EntityType.ARMOR_STAND -> {
                if (blockArmorStands) {
                    event.isCancelled = true
                    player.sendMessage(messageService.getLegacy("inventory.armor-stand"))
                    player.updateInventory()
                }
                return
            }
            EntityType.ALLAY -> {
                if (blockAllays) {
                    event.isCancelled = true
                    player.sendMessage(messageService.getLegacy("inventory.allay"))
                }
                return
            }
            else -> {}
        }

        if (rightClicked is InventoryHolder && rightClicked !is Player) {
            event.isCancelled = true
            player.sendMessage(messageService.getLegacy("inventory.restricted-inventory"))
            player.updateInventory()
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        if (event.click.isKeyboardDrop() || event.slotType == InventoryType.SlotType.OUTSIDE) {
            if (isRealMace(event.currentItem) || isRealMace(event.cursor)) {
                block(event, player)
            }
            return
        }

        if (event.isShiftClick && isRealMace(event.currentItem)) {
            val destination = if (event.clickedInventory?.type == InventoryType.PLAYER) {
                event.view.topInventory
            } else {
                event.view.bottomInventory
            }
            if (isRestricted(destination)) {
                block(event, player)
            }
            return
        }

        if (event.click == ClickType.NUMBER_KEY) {
            val hotbarItem = player.inventory.getItem(event.hotbarButton)
            if (isRealMace(hotbarItem)) {
                val destination = event.clickedInventory
                if (isRestricted(destination)) {
                    block(event, player)
                }
            }
            return
        }

        if (event.click == ClickType.SWAP_OFFHAND) {
            val offhand = player.inventory.itemInOffHand
            if (isRealMace(offhand)) {
                val destination = event.clickedInventory
                if (isRestricted(destination)) {
                    block(event, player)
                }
            }
            return
        }

        if (isRealMace(event.cursor)) {
            val destination = event.clickedInventory ?: return
            if (isRestricted(destination)) {
                block(event, player)
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!isRealMace(event.oldCursor)) {
            return
        }

        val topSize = event.view.topInventory.size
        val topInventory = event.view.topInventory

        for (rawSlot in event.rawSlots) {
            val target = if (rawSlot < topSize) topInventory else event.view.bottomInventory
            if (isRestricted(target)) {
                block(event, player)
                return
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryMove(event: InventoryMoveItemEvent) {
        if (isRealMace(event.item) && isRestricted(event.destination)) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryPickup(event: InventoryPickupItemEvent) {
        if (isRealMace(event.item.itemStack)) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockDispense(event: BlockDispenseEvent) {
        if (isRealMace(event.item)) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        if (!playerIsCarryingMace(player)) {
            return
        }

        val topInventory = event.inventory
        if (topInventory.type in protectedInventories) {
            return
        }

        event.isCancelled = true
        player.sendMessage(messageService.getLegacy("inventory.cannot-open"))
        player.updateInventory()
    }

    private fun playerIsCarryingMace(player: Player): Boolean {
        if (isRealMace(player.inventory.itemInMainHand)) return true
        if (isRealMace(player.inventory.itemInOffHand)) return true
        return isRealMace(player.itemOnCursor)
    }

    private fun block(event: InventoryClickEvent, player: Player) {
        event.isCancelled = true
        player.sendMessage(messageService.getLegacy("inventory.refuses-drop"))
        player.updateInventory()
    }

    private fun block(event: InventoryDragEvent, player: Player) {
        event.isCancelled = true
        player.sendMessage(messageService.getLegacy("inventory.resists-stow"))
        player.updateInventory()
    }

    private fun isRestricted(inventory: Inventory?): Boolean {
        val type = inventory?.type ?: return false
        return type !in protectedInventories
    }

    private fun ClickType.isKeyboardDrop(): Boolean {
        return this == ClickType.DROP || this == ClickType.CONTROL_DROP
    }
}

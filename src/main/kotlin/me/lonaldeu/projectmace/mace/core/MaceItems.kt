package me.lonaldeu.projectmace.mace.core

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

class MaceItems(
    private val keys: MaceKeys,
    private val maxDurability: Int = 500
) {
    // Vanilla mace max durability
    private val vanillaMaxDurability = 500

    fun ensureTaggedMace(itemStack: ItemStack, maceUuid: UUID) {
        val meta = itemStack.itemMeta ?: return
        val pdc = meta.persistentDataContainer
        
        // Tag with mace UUID
        pdc.set(keys.maceTagKey, PersistentDataType.STRING, maceUuid.toString())
        
        // Store custom max durability in PDC (for tracking purposes)
        pdc.set(keys.maxDurabilityKey, PersistentDataType.INTEGER, maxDurability)
        
        // Store current durability (starts at max)
        pdc.set(keys.currentDurabilityKey, PersistentDataType.INTEGER, maxDurability)
        
        // Set visual durability bar (full)
        if (meta is Damageable) {
            meta.damage = 0
        }
        
        itemStack.itemMeta = meta
    }

    fun getMaceUuid(itemStack: ItemStack?): UUID? {
        if (itemStack == null) return null
        if (itemStack.type != Material.MACE) return null
        val meta = itemStack.itemMeta ?: return null
        val pdc = meta.persistentDataContainer
        val id = pdc.get(keys.maceTagKey, PersistentDataType.STRING) ?: return null
        return runCatching { UUID.fromString(id) }.getOrNull()
    }

    fun getMaxDurability(itemStack: ItemStack?): Int {
        if (itemStack == null) return vanillaMaxDurability
        val meta = itemStack.itemMeta ?: return vanillaMaxDurability
        return meta.persistentDataContainer.get(keys.maxDurabilityKey, PersistentDataType.INTEGER) 
            ?: vanillaMaxDurability
    }

    fun getCurrentDurability(itemStack: ItemStack?): Int {
        if (itemStack == null) return 0
        val meta = itemStack.itemMeta ?: return 0
        val pdc = meta.persistentDataContainer
        
        // First check our tracked durability
        val tracked = pdc.get(keys.currentDurabilityKey, PersistentDataType.INTEGER)
        if (tracked != null) return tracked
        
        // Fallback: calculate from vanilla damage
        if (meta is Damageable) {
            return vanillaMaxDurability - meta.damage
        }
        return vanillaMaxDurability
    }

    /**
     * Apply durability damage to a legendary mace.
     * Updates both our PDC tracking and the vanilla durability bar (scaled).
     * @return true if mace should break, false otherwise
     */
    fun applyDamage(itemStack: ItemStack, damageAmount: Int = 1): Boolean {
        val meta = itemStack.itemMeta ?: return false
        val pdc = meta.persistentDataContainer
        
        val maxDur = pdc.get(keys.maxDurabilityKey, PersistentDataType.INTEGER) ?: vanillaMaxDurability
        val currentDur = pdc.get(keys.currentDurabilityKey, PersistentDataType.INTEGER) ?: maxDur
        
        val newDurability = (currentDur - damageAmount).coerceAtLeast(0)
        pdc.set(keys.currentDurabilityKey, PersistentDataType.INTEGER, newDurability)
        
        // Update visual durability bar (scaled to vanilla max so bar looks proportional)
        if (meta is Damageable) {
            val durabilityPercent = newDurability.toDouble() / maxDur.toDouble()
            val vanillaDamage = ((1.0 - durabilityPercent) * vanillaMaxDurability).toInt()
            meta.damage = vanillaDamage.coerceIn(0, vanillaMaxDurability - 1)
        }
        
        itemStack.itemMeta = meta
        
        return newDurability <= 0
    }

    fun giveTaggedMace(player: Player, maceUuid: UUID) {
        val maceItem = ItemStack(Material.MACE)
        ensureTaggedMace(maceItem, maceUuid)
        val leftovers = player.inventory.addItem(maceItem)
        leftovers.values.forEach { leftover ->
            player.world.dropItemNaturally(player.location, leftover)
        }
    }

    fun removeTaggedMaces(player: Player, isRealMace: (ItemStack?) -> Boolean) {
        val inventory = player.inventory
        inventory.contents.forEachIndexed { index, item ->
            if (isRealMace(item)) {
                inventory.setItem(index, null)
            }
        }
        if (isRealMace(player.itemOnCursor)) {
            player.setItemOnCursor(null)
        }
    }
}

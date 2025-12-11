package me.lonaldeu.projectmace.mace.events

import me.lonaldeu.projectmace.mace.core.MaceItems
import me.lonaldeu.projectmace.config.ConfigService
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.enchantment.PrepareItemEnchantEvent
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.inventory.ItemStack

/**
 * Controls enchanting of legendary maces.
 * Allows configuration of which enchantments are permitted and via which methods.
 */
internal class MaceEnchantEvents(
    private val items: MaceItems,
    private val config: ConfigService
) : Listener {

    // ═══════════════════════════════════════════════════════════════════
    // ENCHANTING TABLE
    // ═══════════════════════════════════════════════════════════════════
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPrepareEnchant(event: PrepareItemEnchantEvent) {
        val item = event.item
        if (!isLegendaryMace(item)) return
        
        // Check if enchanting table is allowed
        if (!config.typedConfig.enchanting.enchantingTable) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEnchantItem(event: EnchantItemEvent) {
        val item = event.item
        if (!isLegendaryMace(item)) return
        
        // Check if enchanting table is allowed
        if (!config.typedConfig.enchanting.enchantingTable) {
            event.isCancelled = true
            return
        }
        
        // Filter out blocked enchantments and enforce max levels
        val toRemove = mutableListOf<Enchantment>()
        val toModify = mutableMapOf<Enchantment, Int>()
        
        for ((enchant, level) in event.enchantsToAdd) {
            if (!isEnchantmentAllowed(enchant)) {
                toRemove.add(enchant)
            } else {
                val maxLevel = config.getEnchantMaxLevel(enchant)
                if (maxLevel != null && level > maxLevel) {
                    toModify[enchant] = maxLevel
                }
            }
        }
        
        // Remove blocked enchantments
        toRemove.forEach { event.enchantsToAdd.remove(it) }
        
        // Apply level caps
        toModify.forEach { (enchant, maxLevel) -> 
            event.enchantsToAdd[enchant] = maxLevel 
        }
        
        // If all enchantments were removed, cancel
        if (event.enchantsToAdd.isEmpty()) {
            event.isCancelled = true
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ANVIL
    // ═══════════════════════════════════════════════════════════════════
    
    @EventHandler(priority = EventPriority.HIGH)
    fun onPrepareAnvil(event: PrepareAnvilEvent) {
        val result = event.result ?: return
        if (!isLegendaryMace(result)) return
        
        // Check if any input is a legendary mace
        val firstItem = event.inventory.getItem(0)
        val secondItem = event.inventory.getItem(1)
        
        val isEnchanting = isLegendaryMace(firstItem) || isLegendaryMace(secondItem)
        if (!isEnchanting) return
        
        // Check if anvil is allowed
        if (!config.typedConfig.enchanting.anvil) {
            event.result = null
            return
        }
        
        // Check enchantments on result
        val resultMeta = result.itemMeta ?: return
        val enchants = resultMeta.enchants.toMap()
        var modified = false
        
        for ((enchant, level) in enchants) {
            if (!isEnchantmentAllowed(enchant)) {
                resultMeta.removeEnchant(enchant)
                modified = true
            } else {
                val maxLevel = config.getEnchantMaxLevel(enchant)
                if (maxLevel != null && level > maxLevel) {
                    resultMeta.removeEnchant(enchant)
                    resultMeta.addEnchant(enchant, maxLevel, true)
                    modified = true
                }
            }
        }
        
        if (modified) {
            result.itemMeta = resultMeta
            event.result = result
        }
        
        // If no enchantments remain and we're trying to add some, block it
        if (resultMeta.enchants.isEmpty() && enchants.isNotEmpty()) {
            event.result = null
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════
    
    private fun isLegendaryMace(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.MACE) return false
        return items.getMaceUuid(item) != null
    }
    
    private fun isEnchantmentAllowed(enchant: Enchantment): Boolean {
        val blocked = config.typedConfig.enchanting.blockedEnchantments
        val allowed = config.typedConfig.enchanting.allowedEnchantments
        
        val enchantKey = enchant.key.key.lowercase()
        
        // Blacklist takes priority
        if (blocked.contains(enchantKey)) {
            return false
        }
        
        // If whitelist is empty, allow all (except blocked)
        if (allowed.isEmpty()) {
            return true
        }
        
        // Check whitelist
        return allowed.contains(enchantKey)
    }
}

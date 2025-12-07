package me.lonaldeu.projectmace.mace.events

import me.lonaldeu.projectmace.mace.core.MaceItems
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemDamageEvent
import java.util.UUID

/**
 * Handles custom durability for legendary maces.
 * Lets vanilla handle durability (so Unbreaking enchant works),
 * but tracks our custom durability and scales the visual bar.
 */
internal class MaceDurabilityEvents(
    private val items: MaceItems,
    private val onMaceBreak: (Player, UUID) -> Unit
) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onItemDamage(event: PlayerItemDamageEvent) {
        val item = event.item
        if (item.type != Material.MACE) return
        
        val maceUuid = items.getMaceUuid(item) ?: return
        
        // Let vanilla calculate the damage (Unbreaking enchant already applied by this point)
        // event.damage is the final damage after Unbreaking reduction
        val actualDamage = event.damage
        
        // Cancel vanilla's damage application - we'll handle the visual bar ourselves
        event.isCancelled = true
        
        // Apply our custom durability (tracks in PDC, scales visual bar)
        val shouldBreak = items.applyDamage(item, actualDamage)
        
        if (shouldBreak) {
            onMaceBreak(event.player, maceUuid)
        }
    }
}

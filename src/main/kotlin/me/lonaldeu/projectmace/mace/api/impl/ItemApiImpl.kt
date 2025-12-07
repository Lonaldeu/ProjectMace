package me.lonaldeu.projectmace.mace.api.impl

import me.lonaldeu.projectmace.mace.api.LegendaryMaceItemApi
import me.lonaldeu.projectmace.mace.core.MaceItems
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

internal class ItemApiImpl(
    private val items: MaceItems,
    private val lifecycle: MaceLifecycle
) : LegendaryMaceItemApi {

    override fun ensureTagged(itemStack: ItemStack, maceUuid: UUID) {
        items.ensureTaggedMace(itemStack, maceUuid)
    }

    override fun getMaceUuid(itemStack: ItemStack?): UUID? = items.getMaceUuid(itemStack)

    override fun isLegendaryMace(itemStack: ItemStack?): Boolean = lifecycle.isRealMace(itemStack)

    override fun giveTagged(player: Player, maceUuid: UUID) {
        items.giveTaggedMace(player, maceUuid)
    }

    override fun removeTaggedMaces(player: Player) {
        items.removeTaggedMaces(player, lifecycle::isRealMace)
    }
}

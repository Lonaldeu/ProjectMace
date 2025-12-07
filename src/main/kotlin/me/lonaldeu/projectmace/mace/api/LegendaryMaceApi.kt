package me.lonaldeu.projectmace.mace.api

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Entry point for other plugins to integrate with the Legendary Mace systems.
 *
 * Obtain an instance via [LegendaryMaceApiProvider.get] after ProjectMace has enabled.
 */
interface LegendaryMaceApi {
    val state: LegendaryMaceStateView
    val control: LegendaryMaceControl
    val items: LegendaryMaceItemApi
    val combat: LegendaryMaceCombatApi
}

/**
 * Read-only snapshot style accessors for Legendary Mace state.
 */
interface LegendaryMaceStateView {
    fun getActiveWielders(): Collection<LegendaryMaceWielderView>
    fun findWielder(playerUuid: UUID): LegendaryMaceWielderView?
    fun getLooseMaces(): Collection<LegendaryLooseMaceView>
    fun findLooseMace(maceUuid: UUID): LegendaryLooseMaceView?
    fun getRemainingBloodthirstSeconds(playerUuid: UUID): Double?
    fun getMaxLegendaryMaces(): Int
    fun maceCount(): Int
    fun isLegendaryMace(itemStack: ItemStack?): Boolean
    fun getLegendaryMaceUuid(itemStack: ItemStack?): UUID?
}

/**
 * Mutation helpers for trusted integrations.
 */
interface LegendaryMaceControl {
    fun clearWielder(playerUuid: UUID, reason: String = "api_force_remove"): Boolean
    fun extendBloodthirst(playerUuid: UUID, additionalSeconds: Double): Boolean
    fun setBloodthirst(playerUuid: UUID, timerEndEpochSeconds: Double): Boolean
    fun refreshWielderEffects(player: Player)
    fun giveTaggedMace(player: Player, maceUuid: UUID)
    fun manualDespawn(maceUuid: UUID): Boolean
    fun registerLooseMace(maceUuid: UUID, location: Location, timerEndEpochSeconds: Double?): Boolean
}

/**
 * Item helpers for integrations manipulating inventories.
 */
interface LegendaryMaceItemApi {
    fun ensureTagged(itemStack: ItemStack, maceUuid: UUID)
    fun getMaceUuid(itemStack: ItemStack?): UUID?
    fun isLegendaryMace(itemStack: ItemStack?): Boolean
    fun giveTagged(player: Player, maceUuid: UUID)
    fun removeTaggedMaces(player: Player)
}

/**
 * Combat data helpers.
 */
interface LegendaryMaceCombatApi {
    fun getLastDamageEpochSeconds(playerUuid: UUID): Double?
    fun clearCombatData(playerUuid: UUID)
}

data class LegendaryMaceWielderView(
    val playerUuid: UUID,
    val maceUuid: UUID,
    val bloodthirstEndsAtEpochSeconds: Double,
    val secondsRemaining: Double,
    val lastKillUuid: UUID?
)

data class LegendaryLooseMaceView(
    val maceUuid: UUID,
    val location: Location,
    val timerEndEpochSeconds: Double?,
    val originalOwnerUuid: UUID?
)

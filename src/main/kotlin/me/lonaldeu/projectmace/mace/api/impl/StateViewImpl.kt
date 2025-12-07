package me.lonaldeu.projectmace.mace.api.impl

import me.lonaldeu.projectmace.mace.api.LegendaryLooseMaceView
import me.lonaldeu.projectmace.mace.api.LegendaryMaceStateView
import me.lonaldeu.projectmace.mace.api.LegendaryMaceWielderView
import me.lonaldeu.projectmace.mace.core.MaceItems
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import me.lonaldeu.projectmace.mace.domain.MaceState
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.math.max

internal class StateViewImpl(
    private val state: MaceState,
    private val lifecycle: MaceLifecycle,
    private val items: MaceItems,
    private val maxLegendaryMaces: () -> Int,
    private val nowSeconds: () -> Double
) : LegendaryMaceStateView {

    override fun getActiveWielders(): Collection<LegendaryMaceWielderView> {
        val now = nowSeconds()
        return state.maceWielders.values.map { data ->
            LegendaryMaceWielderView(
                playerUuid = data.playerUuid,
                maceUuid = data.maceUuid,
                bloodthirstEndsAtEpochSeconds = data.timerEndEpochSeconds,
                secondsRemaining = max(0.0, data.timerEndEpochSeconds - now),
                lastKillUuid = data.lastKillUuid
            )
        }
    }

    override fun findWielder(playerUuid: UUID): LegendaryMaceWielderView? {
        val data = state.maceWielders[playerUuid] ?: return null
        val now = nowSeconds()
        return LegendaryMaceWielderView(
            playerUuid = data.playerUuid,
            maceUuid = data.maceUuid,
            bloodthirstEndsAtEpochSeconds = data.timerEndEpochSeconds,
            secondsRemaining = max(0.0, data.timerEndEpochSeconds - now),
            lastKillUuid = data.lastKillUuid
        )
    }

    override fun getLooseMaces(): Collection<LegendaryLooseMaceView> =
        state.looseMaces.values.map { loose ->
            LegendaryLooseMaceView(
                maceUuid = loose.maceUuid,
                location = loose.location.clone(),
                timerEndEpochSeconds = loose.timerEndEpochSeconds,
                originalOwnerUuid = loose.originalOwnerUuid
            )
        }

    override fun findLooseMace(maceUuid: UUID): LegendaryLooseMaceView? {
        val loose = state.looseMaces[maceUuid] ?: return null
        return LegendaryLooseMaceView(
            maceUuid = loose.maceUuid,
            location = loose.location.clone(),
            timerEndEpochSeconds = loose.timerEndEpochSeconds,
            originalOwnerUuid = loose.originalOwnerUuid
        )
    }

    override fun getRemainingBloodthirstSeconds(playerUuid: UUID): Double? {
        val data = state.maceWielders[playerUuid] ?: return null
        return max(0.0, data.timerEndEpochSeconds - nowSeconds())
    }

    override fun getMaxLegendaryMaces(): Int = maxLegendaryMaces()

    override fun maceCount(): Int = state.getAllLegendaryMaceUuids().size

    override fun isLegendaryMace(itemStack: ItemStack?): Boolean = lifecycle.isRealMace(itemStack)

    override fun getLegendaryMaceUuid(itemStack: ItemStack?): UUID? = items.getMaceUuid(itemStack)
}

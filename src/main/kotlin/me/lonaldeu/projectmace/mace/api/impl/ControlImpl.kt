package me.lonaldeu.projectmace.mace.api.impl

import me.lonaldeu.projectmace.mace.api.LegendaryMaceControl
import me.lonaldeu.projectmace.mace.core.MaceChunkControl
import me.lonaldeu.projectmace.mace.core.MaceItems
import me.lonaldeu.projectmace.mace.domain.MaceEffects
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import me.lonaldeu.projectmace.mace.domain.MaceState
import me.lonaldeu.projectmace.mace.domain.model.LooseMace
import me.lonaldeu.projectmace.mace.tasks.MaceDespawnTasks
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.math.max

internal class ControlImpl(
    private val state: MaceState,
    private val lifecycle: MaceLifecycle,
    private val effects: MaceEffects,
    private val items: MaceItems,
    private val chunkControl: MaceChunkControl,
    private val despawnTasks: MaceDespawnTasks,
    private val saveData: () -> Unit,
    private val nowSeconds: () -> Double
) : LegendaryMaceControl {

    override fun clearWielder(playerUuid: UUID, reason: String): Boolean {
        val data = state.maceWielders[playerUuid] ?: return false
        val player = Bukkit.getPlayer(playerUuid)
        lifecycle.maceAbandonsPlayer(player, playerUuid, data, reason, despawnTasks::stopMaceDespawnSequence)
        return true
    }

    override fun extendBloodthirst(playerUuid: UUID, additionalSeconds: Double): Boolean {
        val data = state.maceWielders[playerUuid] ?: return false
        val now = nowSeconds()
        val updated = max(now, data.timerEndEpochSeconds + additionalSeconds)
        data.timerEndEpochSeconds = updated
        saveData()
        data.lastChance = false
        data.lastDesperationWhisperEpoch = now
        Bukkit.getPlayer(playerUuid)?.let { effects.startWielderEffects(it) }
        return true
    }

    override fun setBloodthirst(playerUuid: UUID, timerEndEpochSeconds: Double): Boolean {
        val data = state.maceWielders[playerUuid] ?: return false
        val now = nowSeconds()
        data.timerEndEpochSeconds = max(now, timerEndEpochSeconds)
        data.lastChance = false
        data.lastDesperationWhisperEpoch = now
        saveData()
        Bukkit.getPlayer(playerUuid)?.let { effects.startWielderEffects(it) }
        return true
    }

    override fun refreshWielderEffects(player: Player) {
        effects.startWielderEffects(player)
    }

    override fun giveTaggedMace(player: Player, maceUuid: UUID) {
        items.giveTaggedMace(player, maceUuid)
    }

    override fun manualDespawn(maceUuid: UUID): Boolean {
        val existed = state.looseMaces.containsKey(maceUuid)
        lifecycle.manualDespawnMace(maceUuid, despawnTasks::stopMaceDespawnSequence)
        return existed
    }

    override fun registerLooseMace(
        maceUuid: UUID,
        location: Location,
        timerEndEpochSeconds: Double?
    ): Boolean {
        val existing = state.looseMaces[maceUuid]
        existing?.broadcastTask?.cancel()
        existing?.despawnTask?.cancel()
        val loose = LooseMace(
            maceUuid = maceUuid,
            location = location.clone(),
            timerEndEpochSeconds = timerEndEpochSeconds,
            originalOwnerUuid = existing?.originalOwnerUuid
        )
        state.looseMaces[maceUuid] = loose
        chunkControl.setChunkForceState(location, true)
        despawnTasks.startMaceDespawnSequence(maceUuid)
        saveData()
        return existing == null
    }
}

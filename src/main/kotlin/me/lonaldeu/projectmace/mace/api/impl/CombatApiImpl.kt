package me.lonaldeu.projectmace.mace.api.impl

import me.lonaldeu.projectmace.mace.api.LegendaryMaceCombatApi
import me.lonaldeu.projectmace.mace.domain.MaceCombatService
import me.lonaldeu.projectmace.mace.domain.MaceState
import java.util.UUID

internal class CombatApiImpl(
    private val state: MaceState,
    private val combatService: MaceCombatService
) : LegendaryMaceCombatApi {

    override fun getLastDamageEpochSeconds(playerUuid: UUID): Double? = state.lastDamageTime[playerUuid]

    override fun clearCombatData(playerUuid: UUID) {
        combatService.clearCombatData(playerUuid)
    }
}

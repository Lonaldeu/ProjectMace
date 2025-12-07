package me.lonaldeu.projectmace.mace.api.impl

import me.lonaldeu.projectmace.mace.api.LegendaryMaceApi
import me.lonaldeu.projectmace.mace.api.LegendaryMaceCombatApi
import me.lonaldeu.projectmace.mace.api.LegendaryMaceControl
import me.lonaldeu.projectmace.mace.api.LegendaryMaceItemApi
import me.lonaldeu.projectmace.mace.api.LegendaryMaceStateView

internal class LegendaryMaceManagerApiImpl(
    override val state: LegendaryMaceStateView,
    override val control: LegendaryMaceControl,
    override val items: LegendaryMaceItemApi,
    override val combat: LegendaryMaceCombatApi
) : LegendaryMaceApi

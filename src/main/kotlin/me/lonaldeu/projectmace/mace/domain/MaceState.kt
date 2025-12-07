package me.lonaldeu.projectmace.mace.domain

import me.lonaldeu.projectmace.mace.domain.model.DamageRecord
import me.lonaldeu.projectmace.mace.domain.model.LooseMace
import me.lonaldeu.projectmace.mace.domain.model.MaceWielder
import me.lonaldeu.projectmace.mace.domain.model.TotemRecord
import java.util.UUID

internal class MaceState {
    val maceWielders: MutableMap<UUID, MaceWielder> = mutableMapOf()
    val looseMaces: MutableMap<UUID, LooseMace> = mutableMapOf()
    val pendingMaceRemoval: MutableSet<UUID> = mutableSetOf()
    val lastDamageTime: MutableMap<UUID, Double> = mutableMapOf()
    val recentDamageFrom: MutableMap<UUID, MutableMap<UUID, DamageRecord>> = mutableMapOf()
    val recentTotemPops: MutableMap<UUID, MutableMap<UUID, TotemRecord>> = mutableMapOf()
    val craftCooldowns: MutableMap<UUID, Double> = mutableMapOf()
    
    // For #3: Rate limiting on announcements
    val lastAnnouncementTime: MutableMap<String, Long> = mutableMapOf()

    fun isWielder(playerUuid: UUID): Boolean = maceWielders.containsKey(playerUuid)

    fun getAllLegendaryMaceUuids(): Set<UUID> {
        val fromWielders = maceWielders.values.mapTo(mutableSetOf(), MaceWielder::maceUuid)
        fromWielders.addAll(looseMaces.keys)
        return fromWielders
    }

    fun maceCount(): Int = getAllLegendaryMaceUuids().size
}

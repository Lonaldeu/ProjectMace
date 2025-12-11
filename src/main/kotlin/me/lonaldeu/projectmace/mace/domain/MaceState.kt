package me.lonaldeu.projectmace.mace.domain

import me.lonaldeu.projectmace.mace.domain.model.DamageRecord
import me.lonaldeu.projectmace.mace.domain.model.LooseMace
import me.lonaldeu.projectmace.mace.domain.model.MaceWielder
import me.lonaldeu.projectmace.mace.domain.model.TotemRecord
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class MaceState {
    // Thread-safe maps for Folia compatibility (parallel region execution)
    val maceWielders: MutableMap<UUID, MaceWielder> = ConcurrentHashMap()
    val looseMaces: MutableMap<UUID, LooseMace> = ConcurrentHashMap()
    val pendingMaceRemoval: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val lastDamageTime: MutableMap<UUID, Double> = ConcurrentHashMap()
    val recentDamageFrom: MutableMap<UUID, MutableMap<UUID, DamageRecord>> = ConcurrentHashMap()
    val recentTotemPops: MutableMap<UUID, MutableMap<UUID, TotemRecord>> = ConcurrentHashMap()
    val craftCooldowns: MutableMap<UUID, Double> = ConcurrentHashMap()
    
    // For #3: Rate limiting on announcements
    val lastAnnouncementTime: MutableMap<String, Long> = ConcurrentHashMap()

    fun isWielder(playerUuid: UUID): Boolean = maceWielders.containsKey(playerUuid)

    fun getAllLegendaryMaceUuids(): Set<UUID> {
        val fromWielders = maceWielders.values.mapTo(mutableSetOf(), MaceWielder::maceUuid)
        fromWielders.addAll(looseMaces.keys)
        return fromWielders
    }

    fun maceCount(): Int = getAllLegendaryMaceUuids().size
}

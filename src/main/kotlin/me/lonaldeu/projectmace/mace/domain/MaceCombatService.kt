package me.lonaldeu.projectmace.mace.domain

import me.lonaldeu.projectmace.mace.core.MaceMessaging
import me.lonaldeu.projectmace.mace.core.nowSeconds
import me.lonaldeu.projectmace.mace.domain.model.DamageRecord
import me.lonaldeu.projectmace.mace.domain.model.MaceConstants
import me.lonaldeu.projectmace.mace.domain.model.TotemRecord
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityResurrectEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin

internal class MaceCombatService(
    private val plugin: Plugin,
    private val state: MaceState,
    private val messaging: MaceMessaging,
    private val messageService: me.lonaldeu.projectmace.config.MessageService,
    private val logVerboseEvent: (
        eventType: String,
        playerName: String?,
        playerUuid: UUID?,
        maceUuid: UUID?,
        location: org.bukkit.Location?,
        containerContext: String?,
        outcome: String?,
        reason: String?,
        additionalContext: Map<String, Any?>,
        timerEnd: Double?,
        timeLeft: Double?
    ) -> Unit,
    private val bloodthirstDurationSeconds: () -> Long,
    private val nowSeconds: () -> Double = ::nowSeconds,
    private val combatVoicelines: List<String>,
    private val settings: CombatSettings = CombatSettings()
) : Listener {

    data class KillResult(
        val worthy: Boolean,
        val score: Double,
        val components: Map<String, Double>,
        val reason: String
    )

    data class CombatSettings(
        val enabled: Boolean = true,
        val trackDamage: Boolean = true,
        val awardKills: Boolean = true,
        val sendMessages: Boolean = true,
        val sendVoicelines: Boolean = true,
        val baseDamage: Double = 5.0,
        val damageMultiplier: Double = 1.0,
        val healthMultiplier: Double = 0.5,
        val armorMultiplier: Double = 2.0,
        val totemBonus: Double = 20.0,
        val worthyThreshold: Double = 10.0,
        val easyThreshold: Double = 5.0,
        val holdTimeBaseDamageRequirement: Double = 8.0,
        val holdTimeEscalationRate: Double = 0.5
    )

    fun register() {
        if (!settings.enabled) {
            plugin.logger.info("[Mace] Combat service disabled via config")
            return
        }
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.logger.fine("[Mace] Combat service registered")
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        if (!settings.enabled || !settings.trackDamage) return
        val victim = event.entity as? Player ?: return
        val attacker = resolveAttacker(event) ?: return
        if (attacker.uniqueId == victim.uniqueId) return

        val now = nowSeconds()
        val amount = event.finalDamage.coerceAtLeast(0.0)
        if (amount <= 0.0) return

        recordDamage(victim.uniqueId, attacker.uniqueId, amount, now)
        state.lastDamageTime[victim.uniqueId] = now
        pruneStaleCombatData(now)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityResurrect(event: EntityResurrectEvent) {
        if (!settings.enabled || !settings.trackDamage) return
        val player = event.entity as? Player ?: return
        val lastCause = player.lastDamageCause as? EntityDamageByEntityEvent ?: return
        val attacker = resolveDamagingEntity(lastCause.damager) ?: return
        if (attacker.uniqueId == player.uniqueId) {
            return
        }
        val now = nowSeconds()
        val records = state.recentTotemPops.getOrPut(player.uniqueId) { mutableMapOf() }
        val entry = records[attacker.uniqueId] ?: TotemRecord(popCount = 0, lastPopEpochSeconds = now)
        entry.popCount += 1
        entry.lastPopEpochSeconds = now
        records[attacker.uniqueId] = entry
        pruneStaleCombatData(now)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!settings.enabled || !settings.trackDamage) return
        clearCombatData(event.player.uniqueId)
    }

    fun handlePlayerKill(killer: Player, victim: Player): KillResult {
        if (!settings.enabled) {
            return KillResult(
                worthy = false,
                score = 0.0,
                components = emptyMap(),
                reason = "combat_disabled"
            )
        }

        val wielder = state.maceWielders[killer.uniqueId] ?: return KillResult(
            worthy = false,
            score = 0.0,
            components = emptyMap(),
            reason = "not_wielder"
        )

        val now = nowSeconds()
        val result = evaluateKill(killer, victim, now)

        if (!settings.awardKills) {
            clearCombatData(victim.uniqueId)
            pruneStaleCombatData(now)
            return result.copy(worthy = false, reason = "awards_disabled")
        }

        if (result.worthy) {
            // #1 & #2: Check minimum kill threshold with adaptive difficulty scaling
            val holdTimeMinutes = calculateHoldTime(wielder)
            val minimumDamageThreshold = settings.holdTimeBaseDamageRequirement + 
                (holdTimeMinutes * settings.holdTimeEscalationRate)
            val damageOut = state.recentDamageFrom[victim.uniqueId]?.get(killer.uniqueId)?.totalDamage ?: 0.0
            
            if (damageOut < minimumDamageThreshold) {
                if (settings.sendMessages) {
                    killer.sendMessage(messageService.getLegacy("combat.kill-weak", "score" to formatScore(result.score)))
                }
                clearCombatData(victim.uniqueId)
                pruneStaleCombatData(now)
                return result.copy(worthy = false, reason = "below_minimum_threshold")
            }

            val newTimerEnd = now + bloodthirstDurationSeconds().toDouble()
            val previousTimer = wielder.timerEndEpochSeconds
            wielder.timerEndEpochSeconds = newTimerEnd
            wielder.lastKillUuid = victim.uniqueId
            wielder.lastChance = false
            
            // Update hold time tracking (#7)
            wielder.currentHoldSessionStartEpoch = now
            
            if (settings.sendMessages) {
                killer.sendMessage(messageService.getLegacy("combat.kill-satisfied", "score" to formatScore(result.score)))
            }
            if (settings.sendVoicelines) {
                sendCombatVoiceline(killer)
            }
            logVerboseEvent(
                "KILL",
                killer.name,
                killer.uniqueId,
                wielder.maceUuid,
                killer.location,
                null,
                "BLOODTHIRST_SATED",
                "worthy_kill",
                mapOf(
                    "score" to result.score,
                    "components" to result.components,
                    "victim_uuid" to victim.uniqueId.toString(),
                    "victim_name" to victim.name,
                    "previous_timer_end" to previousTimer,
                    "new_timer_end" to newTimerEnd,
                    "hold_time_minutes" to holdTimeMinutes,
                    "minimum_damage_threshold" to minimumDamageThreshold,
                    "actual_damage" to damageOut
                ),
                newTimerEnd,
                newTimerEnd - now
            )
        } else {
            if (settings.sendMessages) {
                killer.sendMessage(messageService.getLegacy("combat.kill-easy", "score" to formatScore(result.score)))
            }
            logVerboseEvent(
                "KILL",
                killer.name,
                killer.uniqueId,
                wielder.maceUuid,
                killer.location,
                null,
                "INSUFFICIENT",
                result.reason,
                mapOf(
                    "score" to result.score,
                    "components" to result.components,
                    "victim_uuid" to victim.uniqueId.toString(),
                    "victim_name" to victim.name
                ),
                wielder.timerEndEpochSeconds,
                max(0.0, wielder.timerEndEpochSeconds - now)
            )
        }

        clearCombatData(victim.uniqueId)
        pruneStaleCombatData(now)
        return result
    }

    fun clearCombatData(playerUuid: UUID) {
        state.recentDamageFrom.remove(playerUuid)
        state.recentTotemPops.remove(playerUuid)
        state.lastDamageTime.remove(playerUuid)
        state.recentDamageFrom.values.forEach { it.remove(playerUuid) }
        state.recentTotemPops.values.forEach { it.remove(playerUuid) }
    }

    private fun recordDamage(victimUuid: UUID, attackerUuid: UUID, amount: Double, now: Double) {
        val attackerMap = state.recentDamageFrom.getOrPut(victimUuid) { mutableMapOf() }
        val record = attackerMap[attackerUuid]
        if (record == null) {
            attackerMap[attackerUuid] = DamageRecord(
                hitCount = 1,
                totalDamage = amount,
                lastHitEpochSeconds = now,
                firstHitEpochSeconds = now
            )
        } else {
            record.hitCount += 1
            record.totalDamage += amount
            record.lastHitEpochSeconds = now
            if (record.firstHitEpochSeconds > record.lastHitEpochSeconds) {
                record.firstHitEpochSeconds = record.lastHitEpochSeconds
            }
        }
    }

    private fun evaluateKill(killer: Player, victim: Player, now: Double): KillResult {
        val killerUuid = killer.uniqueId
        val victimUuid = victim.uniqueId
        val damageOut = state.recentDamageFrom[victimUuid]?.get(killerUuid)
        val damageIn = state.recentDamageFrom[killerUuid]?.get(victimUuid)
        val pops = state.recentTotemPops[victimUuid]?.get(killerUuid)

        val recentDamageWindow = now - MaceConstants.WORTHY_KILL_DAMAGE_WINDOW_SECONDS
        val validDamage = damageOut != null && damageOut.lastHitEpochSeconds >= recentDamageWindow

        val damageOutScore = clamp01((damageOut?.totalDamage ?: 0.0) / (settings.baseDamage * settings.damageMultiplier))
        val damageInScore = clamp01((damageIn?.totalDamage ?: 0.0) / (settings.baseDamage * settings.damageMultiplier))
        val fightDuration = damageOut?.let { max(0.0, it.lastHitEpochSeconds - it.firstHitEpochSeconds) } ?: 0.0
        val durationScore = clamp01(fightDuration / MaceConstants.WORTHY_KILL_DAMAGE_WINDOW_SECONDS)

        val gearPieces = victim.armorPiecesEquipped()
        val gearScore = clamp01(gearPieces / MaceConstants.WORTHY_KILL_ARMOR_PIECES.toDouble())

        val validPopCount = pops?.takeIf {
            now - it.lastPopEpochSeconds <= MaceConstants.TOTEM_POP_WINDOW_SECONDS
        }?.popCount ?: 0
        val popsScore = clamp01(validPopCount / 2.0)

        val score =
            (damageOutScore * settings.damageMultiplier) +
                (damageInScore * settings.damageMultiplier) +
                (gearScore * settings.armorMultiplier) +
                (durationScore * settings.healthMultiplier) +
                (popsScore * settings.totemBonus)

        val components = mapOf(
            "damage_out" to damageOutScore,
            "damage_in" to damageInScore,
            "gear" to gearScore,
            "duration" to durationScore,
            "pops" to popsScore
        )

        if (!validDamage) {
            return KillResult(
                worthy = false,
                score = score,
                components = components,
                reason = "stale_damage"
            )
        }

        val worthy = score >= settings.worthyThreshold
        val reason = if (worthy) "worthy" else if (score <= settings.easyThreshold) "easy_kill" else "score_below_threshold"
        return KillResult(
            worthy = worthy,
            score = score,
            components = components,
            reason = reason
        )
    }

    private fun pruneStaleCombatData(now: Double) {
        val damageExpiry = now - MaceConstants.FIGHT_GAP_SECONDS
        val damageIterator = state.recentDamageFrom.entries.iterator()
        while (damageIterator.hasNext()) {
            val entry = damageIterator.next()
            val attackerIterator = entry.value.entries.iterator()
            while (attackerIterator.hasNext()) {
                val attackerEntry = attackerIterator.next()
                if (attackerEntry.value.lastHitEpochSeconds < damageExpiry) {
                    attackerIterator.remove()
                }
            }
            if (entry.value.isEmpty()) {
                damageIterator.remove()
            }
        }

        val popExpiry = now - MaceConstants.TOTEM_POP_WINDOW_SECONDS
        val popIterator = state.recentTotemPops.entries.iterator()
        while (popIterator.hasNext()) {
            val entry = popIterator.next()
            val attackerIterator = entry.value.entries.iterator()
            while (attackerIterator.hasNext()) {
                val attackerEntry = attackerIterator.next()
                if (attackerEntry.value.lastPopEpochSeconds < popExpiry) {
                    attackerIterator.remove()
                }
            }
            if (entry.value.isEmpty()) {
                popIterator.remove()
            }
        }

        val lastDamageIterator = state.lastDamageTime.entries.iterator()
        while (lastDamageIterator.hasNext()) {
            val entry = lastDamageIterator.next()
            if (entry.value < damageExpiry) {
                lastDamageIterator.remove()
            }
        }

        // #4: Auto-prune combat logs that exceed max history per player
        val damageFromIterator = state.recentDamageFrom.values.iterator()
        while (damageFromIterator.hasNext()) {
            val attackerMap = damageFromIterator.next()
            if (attackerMap.size > MaceConstants.COMBAT_LOG_MAX_HISTORY_PER_PLAYER) {
                // Remove oldest entries (by tracking oldest first hit time across all attackers)
                val sorted = attackerMap.entries.sortedBy { it.value.firstHitEpochSeconds }
                val toRemove = sorted.size - MaceConstants.COMBAT_LOG_MAX_HISTORY_PER_PLAYER
                sorted.take(toRemove).forEach { attackerMap.remove(it.key) }
            }
        }

        val popIterator2 = state.recentTotemPops.values.iterator()
        while (popIterator2.hasNext()) {
            val popsMap = popIterator2.next()
            if (popsMap.size > MaceConstants.COMBAT_LOG_MAX_HISTORY_PER_PLAYER) {
                val sorted = popsMap.entries.sortedBy { it.value.lastPopEpochSeconds }
                val toRemove = sorted.size - MaceConstants.COMBAT_LOG_MAX_HISTORY_PER_PLAYER
                sorted.take(toRemove).forEach { popsMap.remove(it.key) }
            }
        }
    }

    private fun resolveAttacker(event: EntityDamageByEntityEvent): Player? {
        return resolveDamagingEntity(event.damager) as? Player
    }

    private fun resolveDamagingEntity(entity: org.bukkit.entity.Entity?): LivingEntity? {
        return when (entity) {
            is Player -> entity
            is Projectile -> entity.shooter as? LivingEntity
            else -> null
        }
    }

    private fun sendCombatVoiceline(player: Player) {
        if (!settings.sendVoicelines || combatVoicelines.isEmpty() || !player.isOnline) return
        val line = combatVoicelines[Random.nextInt(combatVoicelines.size)]
        player.sendMessage(messageService.getLegacy("combat.voiceline-format", "line" to line))
    }

    private fun Player.armorPiecesEquipped(): Int =
        inventory.armorContents.count { it != null && !it.type.isAir }

    private fun clamp01(value: Double): Double = when {
        value <= 0.0 -> 0.0
        value >= 1.0 -> 1.0
        value.isNaN() -> 0.0
        else -> value
    }

    private fun formatScore(score: Double): String = "%.2f".format(Locale.US, min(1.0, max(0.0, score)))

    /**
     * #7: Calculate total hold time for a wielder in minutes
     * Combines stored total + current session duration
     */
    private fun calculateHoldTime(wielder: me.lonaldeu.projectmace.mace.domain.model.MaceWielder): Long {
        val currentSessionDuration = if (wielder.currentHoldSessionStartEpoch != null) {
            val sessionSeconds = (nowSeconds() - wielder.currentHoldSessionStartEpoch!!).toLong()
            sessionSeconds / 60
        } else {
            0L
        }
        return wielder.totalHoldTimeMinutes + currentSessionDuration
    }
}

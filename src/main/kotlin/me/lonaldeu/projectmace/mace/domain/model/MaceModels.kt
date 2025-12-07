package me.lonaldeu.projectmace.mace.domain.model

import me.lonaldeu.projectmace.platform.PlatformScheduler
import org.bukkit.Location
import java.util.UUID

data class MaceWielder(
    val playerUuid: UUID,
    val maceUuid: UUID,
    var timerEndEpochSeconds: Double,
    var lastDesperationWhisperEpoch: Double = 0.0,
    var particleTask: PlatformScheduler.TaskHandle? = null,
    var soundTask: PlatformScheduler.TaskHandle? = null,
    var lastChance: Boolean = false,
    var lastKillUuid: UUID? = null,
    var totalHoldTimeMinutes: Long = 0L,  // Cumulative time held (for #7)
    var currentHoldSessionStartEpoch: Double? = null  // When current session started (for #7)
)

data class LooseMace(
    val maceUuid: UUID,
    var location: Location,
    var timerEndEpochSeconds: Double? = null,
    var originalOwnerUuid: UUID? = null,
    var lastChance: Boolean = false,
    var preservedTimerEndEpochSeconds: Double? = null,
    var despawnTask: PlatformScheduler.TaskHandle? = null,
    var broadcastTask: PlatformScheduler.TaskHandle? = null
)

data class DamageRecord(
    var hitCount: Int,
    var totalDamage: Double,
    var lastHitEpochSeconds: Double,
    var firstHitEpochSeconds: Double
)

data class TotemRecord(
    var popCount: Int,
    var lastPopEpochSeconds: Double
)

object MaceConstants {
    const val DEFAULT_MAX_LEGENDARY_MACES: Int = 3
    const val BLOODTHIRST_DURATION_SECONDS: Long = 24L * 60L * 60L
    const val WORTHY_KILL_ARMOR_PIECES: Int = 4
    const val WORTHY_KILL_DAMAGE_WINDOW_SECONDS: Long = 30

    const val FIGHT_GAP_SECONDS: Long = 60
    const val WORTHY_DAMAGE_TARGET_30S: Double = 80.0
    const val SCORE_WEIGHT_DAMAGE_IN: Double = 0.35
    const val SCORE_WEIGHT_DAMAGE_OUT: Double = 0.30
    const val SCORE_WEIGHT_GEAR: Double = 0.15
    const val SCORE_WEIGHT_DURATION: Double = 0.15
    const val SCORE_WEIGHT_POPS: Double = 0.05
    const val SCORE_WORTHY_THRESHOLD: Double = 0.50
    const val TOTEM_POP_WINDOW_SECONDS: Long = 600
    const val MACE_DESPAWN_TICKS: Long = 20L * 60L * 5L

    // New constants for #1: Minimum kill threshold
    const val MIN_KILL_DAMAGE_BASELINE: Double = 8.0
    // For #2: Escalation multiplier per minute held
    const val HOLD_TIME_ESCALATION_RATE: Double = 0.5  // Damage requirement increases 0.5 per minute held

    // For #3: Rate limiting on announcements (milliseconds)
    const val ANNOUNCEMENT_COOLDOWN_MS: Long = 2000

    // For #4: Memory cleanup constants
    const val COMBAT_LOG_MAX_HISTORY_PER_PLAYER: Int = 1000
    const val COMBAT_LOG_PRUNING_CHECK_INTERVAL_TICKS: Long = 20L * 60L  // Every minute

    // For #7: Placeholder refresh rate
    const val PLACEHOLDER_UPDATE_INTERVAL_TICKS: Long = 20L  // Every second

    const val MACE_DATA_FILE = "mace.yml"
    const val VERBOSE_LOGGING = true
}

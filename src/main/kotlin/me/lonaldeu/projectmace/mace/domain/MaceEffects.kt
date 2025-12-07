package me.lonaldeu.projectmace.mace.domain

import me.lonaldeu.projectmace.mace.domain.model.MaceConstants
import me.lonaldeu.projectmace.mace.core.nowSeconds
import me.lonaldeu.projectmace.platform.PlatformScheduler
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player

internal class MaceEffects(
    private val state: MaceState,
    private val scheduler: PlatformScheduler,
    private val bloodthirstDurationSeconds: () -> Long,
    private val particleIntervalTicks: Long,
    private val particleCount: Int,
    private val soundVolume: Float,
    private val soundPitchMin: Float,
    private val soundPitchMax: Float,
    private val soundHeartbeatThresholdSeconds: Double,
    private val nowSeconds: () -> Double = ::nowSeconds
) {

    fun startWielderEffects(player: Player) {
        val wielder = state.maceWielders[player.uniqueId] ?: return
        stopWielderEffects(player)

        wielder.particleTask = scheduler.runGlobalRepeating(1L, particleIntervalTicks, Runnable {
            if (!player.isOnline || !state.isWielder(player.uniqueId)) {
                stopWielderEffects(player)
                return@Runnable
            }
            scheduler.runAtEntity(player) {
                val timeLeft = wielder.timerEndEpochSeconds - nowSeconds()
                val particle = when {
                    timeLeft > bloodthirstDurationSeconds() * 0.5 -> Particle.CRIMSON_SPORE
                    timeLeft > bloodthirstDurationSeconds() * 0.1 -> Particle.SOUL_FIRE_FLAME
                    else -> Particle.LAVA
                }
                player.world.spawnParticle(particle, player.location.add(0.0, 1.0, 0.0), particleCount, 0.5, 0.5, 0.5, 0.0)
            }
        })

        wielder.soundTask = scheduler.runGlobalRepeating(1L, 40L, Runnable {
            if (!player.isOnline || !state.isWielder(player.uniqueId)) {
                stopWielderEffects(player)
                return@Runnable
            }
            scheduler.runAtEntity(player) {
                val timeLeft = wielder.timerEndEpochSeconds - nowSeconds()
                if (timeLeft < soundHeartbeatThresholdSeconds) {
                    val pitchRange = soundPitchMax - soundPitchMin
                    val pitch = soundPitchMax - (timeLeft / soundHeartbeatThresholdSeconds * pitchRange)
                    player.playSound(player.location, Sound.ENTITY_WARDEN_HEARTBEAT, soundVolume, pitch.toFloat())
                }
            }
        })
    }

    fun stopWielderEffects(player: Player) {
        val wielder = state.maceWielders[player.uniqueId] ?: return
        wielder.particleTask?.cancel()
        wielder.soundTask?.cancel()
        wielder.particleTask = null
        wielder.soundTask = null
    }
}

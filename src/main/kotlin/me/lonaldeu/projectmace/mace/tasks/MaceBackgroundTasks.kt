package me.lonaldeu.projectmace.mace.tasks

import me.lonaldeu.projectmace.mace.domain.model.MaceConstants
import me.lonaldeu.projectmace.mace.core.MaceMessaging
import me.lonaldeu.projectmace.mace.core.nowSeconds
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import me.lonaldeu.projectmace.mace.domain.MaceState
import me.lonaldeu.projectmace.platform.PlatformScheduler
import me.lonaldeu.projectmace.platform.PlatformScheduler.TaskHandle
import java.util.UUID
import kotlin.random.Random

internal class MaceBackgroundTasks(
    private val scheduler: PlatformScheduler,
    private val state: MaceState,
    private val lifecycle: MaceLifecycle,
    private val stopMaceDespawnSequence: (UUID) -> Unit,
    private val messaging: MaceMessaging,
    private val flushDataIfDirty: (Boolean) -> Unit,
    private val bloodthirstDurationSeconds: () -> Long,
    private val lastChanceThresholdSeconds: Double,
    private val idleWhisperIntervalTicks: Long,
    private val warningThresholdPercentage: Double,
    private val nowSeconds: () -> Double = ::nowSeconds,
    private val idleVoicelines: List<String>,
    private val nearingExpiryVoicelines: List<String>,
    private val lastChanceVoicelines: List<String>
) {

    private var whisperTask: TaskHandle? = null
    private val recurringTasks = mutableListOf<TaskHandle>()

    fun start() {
        whisperTask = scheduler.runGlobalRepeating(idleWhisperIntervalTicks, idleWhisperIntervalTicks, Runnable {
            idleWhisperTask()
        })

        recurringTasks += scheduler.runGlobalRepeating(200L, 200L, Runnable {
            lifecycle.checkMaceStatus(
                manualDespawn = { lifecycle.manualDespawnMace(it, stopMaceDespawnSequence) },
                stopMaceDespawnSequence = stopMaceDespawnSequence
            )
        })

        recurringTasks += scheduler.runGlobalRepeating(600L, 600L, Runnable {
            flushDataIfDirty(false)
        })
    }

    fun stop() {
        whisperTask?.cancel()
        whisperTask = null

        recurringTasks.forEach(TaskHandle::cancel)
        recurringTasks.clear()
    }

    private fun idleWhisperTask() {
        val now = nowSeconds()
        state.maceWielders.values.mapNotNull { wielder ->
            org.bukkit.Bukkit.getPlayer(wielder.playerUuid)?.takeIf { it.isOnline }
        }.forEach { player ->
            val wielderData = state.maceWielders[player.uniqueId] ?: return@forEach
            val timeLeft = wielderData.timerEndEpochSeconds - now
            val lastWhisperAgo = now - wielderData.lastDesperationWhisperEpoch
            if (timeLeft <= lastChanceThresholdSeconds && !wielderData.lastChance) {
                val lines = if (lastChanceVoicelines.isNotEmpty()) lastChanceVoicelines else nearingExpiryVoicelines
                whisperToWielder(player, lines)
                wielderData.lastChance = true
                wielderData.lastDesperationWhisperEpoch = now
                return@forEach
            }
            if (timeLeft <= bloodthirstDurationSeconds() * warningThresholdPercentage && lastWhisperAgo > 300) {
                whisperToWielder(player, nearingExpiryVoicelines)
                wielderData.lastDesperationWhisperEpoch = now
            } else if (timeLeft > 0 && Random.nextDouble() < 0.1 && !wielderData.lastChance) {
                whisperToWielder(player, idleVoicelines)
            }
        }
    }

    private fun whisperToWielder(player: org.bukkit.entity.Player, messages: List<String>) {
        if (!player.isOnline || messages.isEmpty()) return
        val message = messages.random()
        messaging.sendLegacyMessage(player, "&4&o$message")
        player.playSound(
            player.location,
            org.bukkit.Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH,
            0.2f,
            0.5f
        )
    }
}

package me.lonaldeu.projectmace.mace.core

import com.google.gson.Gson
import me.lonaldeu.projectmace.ProjectMacePlugin
import me.lonaldeu.projectmace.mace.domain.model.MaceConstants
import org.bukkit.Location
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles verbose event logging for the Legendary Mace.
 */
class LegendaryMaceLogger(
    private val plugin: ProjectMacePlugin,
    private val gson: Gson = Gson()
) {

    fun logVerboseEvent(
        eventType: String,
        playerName: String? = null,
        playerUuid: java.util.UUID? = null,
        maceUuid: java.util.UUID? = null,
        location: Location? = null,
        containerContext: String? = null,
        outcome: String? = null,
        reason: String? = null,
        additionalContext: Map<String, Any?> = emptyMap(),
        timerEnd: Double? = null,
        timeLeft: Double? = null
    ) {
        if (!MaceConstants.VERBOSE_LOGGING) return

        try {
            val logDirectory = File(plugin.dataFolder, "mace_logs")
            if (!logDirectory.exists()) {
                logDirectory.mkdirs()
            }
            val now = Date()
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(now)
            val logDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
            val logFile = File(logDirectory, "mace_log-$logDate.txt")

            val data = buildMap<String, Any?> {
                put("timestamp", timestamp)
                put("event_type", eventType.uppercase(Locale.US))
                playerName?.let { put("player_name", it) }
                playerUuid?.let { put("player_uuid", it.toString()) }
                maceUuid?.let { put("mace_uuid", it.toString()) }
                containerContext?.let { put("container_context", it) }
                outcome?.let { put("outcome", it) }
                reason?.let { put("reason", it) }
                timerEnd?.let { put("timer_end", it) }
                timeLeft?.let { put("time_left", it) }
                if (additionalContext.isNotEmpty()) {
                    put("additional_context", additionalContext)
                }
                location?.let { loc ->
                    val world = loc.world?.name ?: return@let
                    put(
                        "location",
                        mapOf(
                            "world" to world,
                            "x" to loc.blockX,
                            "y" to loc.blockY,
                            "z" to loc.blockZ
                        )
                    )
                }
            }.filterValues { value ->
                when (value) {
                    null -> false
                    is Map<*, *> -> value.isNotEmpty()
                    else -> true
                }
            }

            FileWriter(logFile, true).use { writer ->
                writer.appendLine(gson.toJson(data))
            }
        } catch (ex: Exception) {
            plugin.logger.severe("[Mace] Failed to write verbose log: ${ex.message}")
        }
    }
}

package me.lonaldeu.projectmace.mace.persistence

import me.lonaldeu.projectmace.ProjectMacePlugin
import me.lonaldeu.projectmace.mace.domain.model.MaceConstants
import me.lonaldeu.projectmace.mace.domain.model.MaceWielder
import me.lonaldeu.projectmace.mace.domain.model.LooseMace
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.jvm.Volatile

/**
 * YAML-based persistence for Legendary Mace state.
 * Simple file storage, good for small servers.
 */
internal class LegendaryMacePersistence(
    private val plugin: ProjectMacePlugin,
    private val maceWielders: MutableMap<UUID, MaceWielder>,
    private val looseMaces: MutableMap<UUID, LooseMace>,
    private val pendingMaceRemoval: MutableSet<UUID>,
    private val startWielderEffects: (Player) -> Unit
) : MacePersistence {

    private val configFile: File
    private val config: YamlConfiguration

    @Volatile
    private var dataDirty: Boolean = false

    init {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }
        configFile = File(plugin.dataFolder, MaceConstants.MACE_DATA_FILE)
        if (!configFile.exists()) {
            configFile.createNewFile()
        }
        config = YamlConfiguration.loadConfiguration(configFile)
    }

    override fun loadData(): List<UUID> {
        maceWielders.clear()
        looseMaces.clear()
        pendingMaceRemoval.clear()

        loadWielders(config.getConfigurationSection("mace_wielders"))
        val looseForResume = loadLooseMaces(config.getConfigurationSection("loose_maces"))
        config.getStringList("pending_mace_removal")
            .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            .forEach(pendingMaceRemoval::add)
        return looseForResume
    }

    override fun markDirty() {
        dataDirty = true
    }

    override fun flushIfDirty(force: Boolean) {
        if (!force && !dataDirty) {
            return
        }

        config.set("mace_wielders", null)
        config.set("loose_maces", null)

        maceWielders.forEach { (uuid, data) ->
            val path = "mace_wielders.$uuid"
            config.set("$path.mace_uuid", data.maceUuid.toString())
            config.set("$path.timer_end", data.timerEndEpochSeconds.toString())
            config.set("$path.last_chance", data.lastChance)
            data.lastKillUuid?.let { config.set("$path.last_kill_uuid", it.toString()) }
            // #7: Save hold time data
            config.set("$path.total_hold_time_minutes", data.totalHoldTimeMinutes)
            data.currentHoldSessionStartEpoch?.let { config.set("$path.current_hold_session_start", it.toString()) }
        }

        looseMaces.forEach { (uuid, data) ->
            val path = "loose_maces.$uuid"
            config.set("$path.world", data.location.world?.name)
            config.set("$path.x", data.location.x)
            config.set("$path.y", data.location.y)
            config.set("$path.z", data.location.z)
            data.timerEndEpochSeconds?.let { config.set("$path.timer_end", it.toString()) }
            data.originalOwnerUuid?.let { config.set("$path.original_owner_uuid", it.toString()) }
            config.set("$path.last_chance", data.lastChance)
        }

        config.set("pending_mace_removal", pendingMaceRemoval.map(UUID::toString))

        try {
            config.save(configFile)
            dataDirty = false
        } catch (ex: IOException) {
            plugin.logger.severe("Failed to save mace data: ${ex.message}")
        }
    }

    private fun loadWielders(section: ConfigurationSection?) {
        section ?: return
        for (key in section.getKeys(false)) {
            val playerUuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            val maceUuid = section.getString("$key.mace_uuid")?.let { uuidStr ->
                runCatching { UUID.fromString(uuidStr) }.getOrNull()
            } ?: continue
            val timerEnd = section.getString("$key.timer_end")?.toDoubleOrNull() ?: continue
            val lastChance = section.getBoolean("$key.last_chance", false)
            val lastKill = section.getString("$key.last_kill_uuid")?.let { runCatching { UUID.fromString(it) }.getOrNull() }

            // #7: Load hold time data
            val totalHoldTimeMinutes = section.getLong("$key.total_hold_time_minutes", 0L)
            val currentHoldSessionStart = section.getString("$key.current_hold_session_start")?.toDoubleOrNull()

            val wielder = MaceWielder(
                playerUuid = playerUuid,
                maceUuid = maceUuid,
                timerEndEpochSeconds = timerEnd,
                lastChance = lastChance,
                lastKillUuid = lastKill,
                totalHoldTimeMinutes = totalHoldTimeMinutes,
                currentHoldSessionStartEpoch = currentHoldSessionStart
            )
            maceWielders[playerUuid] = wielder

            Bukkit.getPlayer(playerUuid)?.takeIf { it.isOnline }?.let(startWielderEffects)
        }
    }

    private fun loadLooseMaces(section: ConfigurationSection?): List<UUID> {
        section ?: return emptyList()
        val looseForResume = mutableListOf<UUID>()
        for (key in section.getKeys(false)) {
            val maceUuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            val worldName = section.getString("$key.world") ?: continue
            val world = Bukkit.getWorld(worldName) ?: continue
            val x = section.getDouble("$key.x")
            val y = section.getDouble("$key.y")
            val z = section.getDouble("$key.z")
            val timerEnd = section.getString("$key.timer_end")?.toDoubleOrNull()
            val originalOwner = section.getString("$key.original_owner_uuid")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val lastChance = section.getBoolean("$key.last_chance", false)

            val loose = LooseMace(
                maceUuid = maceUuid,
                location = org.bukkit.Location(world, x, y, z),
                timerEndEpochSeconds = timerEnd,
                originalOwnerUuid = originalOwner,
                lastChance = lastChance
            )
            looseMaces[maceUuid] = loose
            looseForResume += maceUuid
        }
        return looseForResume
    }
    override fun trackWielderRemoval(playerUuid: UUID) {
        // No-op for YAML: whole file is rewritten on save
    }

    override fun trackLooseMaceRemoval(maceUuid: UUID) {
        // No-op for YAML: whole file is rewritten on save
    }
}

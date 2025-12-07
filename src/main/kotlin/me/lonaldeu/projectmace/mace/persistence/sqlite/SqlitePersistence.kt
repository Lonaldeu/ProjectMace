package me.lonaldeu.projectmace.mace.persistence.sqlite

import me.lonaldeu.projectmace.ProjectMacePlugin
import me.lonaldeu.projectmace.mace.domain.model.MaceWielder
import me.lonaldeu.projectmace.mace.domain.model.LooseMace
import me.lonaldeu.projectmace.mace.persistence.MacePersistence
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import java.util.logging.Logger

/**
 * SQLite-based persistence for mace data.
 * Better performance for large numbers of maces.
 */
internal class SqlitePersistence(
    private val plugin: ProjectMacePlugin,
    private val maceWielders: MutableMap<UUID, MaceWielder>,
    private val looseMaces: MutableMap<UUID, LooseMace>,
    private val pendingMaceRemoval: MutableSet<UUID>,
    private val startWielderEffects: (Player) -> Unit,
    private val logger: Logger = plugin.logger
) : MacePersistence {

    private val dbFile: File = File(plugin.dataFolder, "mace_data.db")
    private var connection: Connection? = null
    
    @Volatile
    private var dataDirty: Boolean = false
    
    init {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }
    }
    
    private fun getConnection(): Connection {
        connection?.let { conn ->
            if (!conn.isClosed) return conn
        }
        
        val conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        conn.autoCommit = false
        connection = conn
        return conn
    }
    
    private fun initializeTables() {
        val conn = getConnection()
        conn.createStatement().use { stmt ->
            // Wielders table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS mace_wielders (
                    player_uuid TEXT PRIMARY KEY,
                    mace_uuid TEXT NOT NULL,
                    timer_end REAL NOT NULL,
                    last_chance INTEGER DEFAULT 0,
                    last_kill_uuid TEXT,
                    total_hold_time_minutes INTEGER DEFAULT 0,
                    current_hold_session_start REAL
                )
            """.trimIndent())
            
            // Loose maces table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS loose_maces (
                    mace_uuid TEXT PRIMARY KEY,
                    world TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    timer_end REAL,
                    original_owner_uuid TEXT,
                    last_chance INTEGER DEFAULT 0
                )
            """.trimIndent())
            
            // Pending removal table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS pending_mace_removal (
                    mace_uuid TEXT PRIMARY KEY
                )
            """.trimIndent())
            
            // Create indexes for faster lookups
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_wielders_mace ON mace_wielders(mace_uuid)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_loose_world ON loose_maces(world)")
        }
        conn.commit()
        logger.info("[SQLite] Database tables initialized")
    }
    
    override fun loadData(): List<UUID> {
        try {
            initializeTables()
            
            maceWielders.clear()
            looseMaces.clear()
            pendingMaceRemoval.clear()
            
            loadWielders()
            val looseForResume = loadLooseMaces()
            loadPendingRemoval()
            
            logger.info("[SQLite] Loaded ${maceWielders.size} wielders, ${looseMaces.size} loose maces")
            return looseForResume
            
        } catch (e: SQLException) {
            logger.severe("[SQLite] Failed to load data: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }
    
    private fun loadWielders() {
        val conn = getConnection()
        conn.prepareStatement("SELECT * FROM mace_wielders").use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val playerUuid = UUID.fromString(rs.getString("player_uuid"))
                    val maceUuid = UUID.fromString(rs.getString("mace_uuid"))
                    val timerEnd = rs.getDouble("timer_end")
                    val lastChance = rs.getInt("last_chance") == 1
                    val lastKillUuid = rs.getString("last_kill_uuid")?.let { 
                        runCatching { UUID.fromString(it) }.getOrNull() 
                    }
                    val totalHoldTime = rs.getLong("total_hold_time_minutes")
                    val holdSessionStart = rs.getDouble("current_hold_session_start").takeIf { !rs.wasNull() }
                    
                    val wielder = MaceWielder(
                        playerUuid = playerUuid,
                        maceUuid = maceUuid,
                        timerEndEpochSeconds = timerEnd,
                        lastChance = lastChance,
                        lastKillUuid = lastKillUuid,
                        totalHoldTimeMinutes = totalHoldTime,
                        currentHoldSessionStartEpoch = holdSessionStart
                    )
                    maceWielders[playerUuid] = wielder
                    
                    Bukkit.getPlayer(playerUuid)?.takeIf { it.isOnline }?.let(startWielderEffects)
                }
            }
        }
    }
    
    private fun loadLooseMaces(): List<UUID> {
        val looseForResume = mutableListOf<UUID>()
        val conn = getConnection()
        
        conn.prepareStatement("SELECT * FROM loose_maces").use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val maceUuid = UUID.fromString(rs.getString("mace_uuid"))
                    val worldName = rs.getString("world")
                    val world = Bukkit.getWorld(worldName) ?: continue
                    val x = rs.getDouble("x")
                    val y = rs.getDouble("y")
                    val z = rs.getDouble("z")
                    val timerEnd = rs.getDouble("timer_end").takeIf { !rs.wasNull() }
                    val originalOwner = rs.getString("original_owner_uuid")?.let {
                        runCatching { UUID.fromString(it) }.getOrNull()
                    }
                    val lastChance = rs.getInt("last_chance") == 1
                    
                    val loose = LooseMace(
                        maceUuid = maceUuid,
                        location = Location(world, x, y, z),
                        timerEndEpochSeconds = timerEnd,
                        originalOwnerUuid = originalOwner,
                        lastChance = lastChance
                    )
                    looseMaces[maceUuid] = loose
                    looseForResume += maceUuid
                }
            }
        }
        
        return looseForResume
    }
    
    private fun loadPendingRemoval() {
        val conn = getConnection()
        conn.prepareStatement("SELECT mace_uuid FROM pending_mace_removal").use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val uuid = runCatching { UUID.fromString(rs.getString("mace_uuid")) }.getOrNull()
                    if (uuid != null) {
                        pendingMaceRemoval.add(uuid)
                    }
                }
            }
        }
    }
    
    override fun markDirty() {
        dataDirty = true
    }
    
    override fun flushIfDirty(force: Boolean) {
        if (!force && !dataDirty) return
        
        try {
            val conn = getConnection()
            
            // Clear all tables and reinsert (simple approach)
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("DELETE FROM mace_wielders")
                stmt.executeUpdate("DELETE FROM loose_maces")
                stmt.executeUpdate("DELETE FROM pending_mace_removal")
            }
            
            // Insert wielders
            conn.prepareStatement("""
                INSERT INTO mace_wielders 
                (player_uuid, mace_uuid, timer_end, last_chance, last_kill_uuid, total_hold_time_minutes, current_hold_session_start)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { stmt ->
                maceWielders.forEach { (uuid, data) ->
                    stmt.setString(1, uuid.toString())
                    stmt.setString(2, data.maceUuid.toString())
                    stmt.setDouble(3, data.timerEndEpochSeconds)
                    stmt.setInt(4, if (data.lastChance) 1 else 0)
                    stmt.setString(5, data.lastKillUuid?.toString())
                    stmt.setLong(6, data.totalHoldTimeMinutes)
                    val holdSessionStart = data.currentHoldSessionStartEpoch
                    if (holdSessionStart != null) {
                        stmt.setDouble(7, holdSessionStart)
                    } else {
                        stmt.setNull(7, java.sql.Types.REAL)
                    }
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            
            // Insert loose maces
            conn.prepareStatement("""
                INSERT INTO loose_maces 
                (mace_uuid, world, x, y, z, timer_end, original_owner_uuid, last_chance)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { stmt ->
                looseMaces.forEach { (uuid, data) ->
                    stmt.setString(1, uuid.toString())
                    stmt.setString(2, data.location.world?.name ?: "world")
                    stmt.setDouble(3, data.location.x)
                    stmt.setDouble(4, data.location.y)
                    stmt.setDouble(5, data.location.z)
                    val timerEnd = data.timerEndEpochSeconds
                    if (timerEnd != null) {
                        stmt.setDouble(6, timerEnd)
                    } else {
                        stmt.setNull(6, java.sql.Types.REAL)
                    }
                    stmt.setString(7, data.originalOwnerUuid?.toString())
                    stmt.setInt(8, if (data.lastChance) 1 else 0)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            
            // Insert pending removal
            conn.prepareStatement("INSERT INTO pending_mace_removal (mace_uuid) VALUES (?)").use { stmt ->
                pendingMaceRemoval.forEach { uuid ->
                    stmt.setString(1, uuid.toString())
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            
            conn.commit()
            dataDirty = false
            
        } catch (e: SQLException) {
            logger.severe("[SQLite] Failed to save data: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun close() {
        try {
            connection?.close()
            connection = null
        } catch (e: SQLException) {
            logger.warning("[SQLite] Error closing connection: ${e.message}")
        }
    }
}

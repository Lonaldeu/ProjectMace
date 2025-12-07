package me.lonaldeu.projectmace.mace.persistence

import me.lonaldeu.projectmace.ProjectMacePlugin
import me.lonaldeu.projectmace.mace.domain.model.MaceWielder
import me.lonaldeu.projectmace.mace.domain.model.LooseMace
import me.lonaldeu.projectmace.mace.persistence.sqlite.SqliteDriverLoader
import me.lonaldeu.projectmace.mace.persistence.sqlite.SqlitePersistence
import org.bukkit.entity.Player
import java.util.UUID
import java.util.logging.Logger

/**
 * Factory for creating the appropriate persistence implementation
 * based on config settings. Handles automatic migration between storage types.
 */
internal class PersistenceFactory(
    private val plugin: ProjectMacePlugin,
    private val logger: Logger = plugin.logger
) {
    
    enum class StorageType {
        YAML, SQLITE
    }
    
    /**
     * Create the persistence implementation based on config.
     * Automatically handles SQLite driver download and data migration.
     */
    fun create(
        storageType: String,
        maceWielders: MutableMap<UUID, MaceWielder>,
        looseMaces: MutableMap<UUID, LooseMace>,
        pendingMaceRemoval: MutableSet<UUID>,
        startWielderEffects: (Player) -> Unit
    ): MacePersistence {
        val type = parseStorageType(storageType)
        
        return when (type) {
            StorageType.YAML -> {
                logger.info("[Storage] Using YAML storage")
                createYamlPersistence(maceWielders, looseMaces, pendingMaceRemoval, startWielderEffects)
            }
            StorageType.SQLITE -> {
                logger.info("[Storage] SQLite storage requested")
                createSqlitePersistence(maceWielders, looseMaces, pendingMaceRemoval, startWielderEffects)
            }
        }
    }
    
    private fun parseStorageType(value: String): StorageType {
        return when (value.lowercase().trim()) {
            "sqlite", "sql", "db", "database" -> StorageType.SQLITE
            else -> StorageType.YAML
        }
    }
    
    private fun createYamlPersistence(
        maceWielders: MutableMap<UUID, MaceWielder>,
        looseMaces: MutableMap<UUID, LooseMace>,
        pendingMaceRemoval: MutableSet<UUID>,
        startWielderEffects: (Player) -> Unit
    ): MacePersistence {
        val yamlPersistence = LegendaryMacePersistence(
            plugin, maceWielders, looseMaces, pendingMaceRemoval, startWielderEffects
        )
        
        // Check if migration from SQLite is needed
        migrateFromSqliteIfNeeded(yamlPersistence, maceWielders, looseMaces, pendingMaceRemoval, startWielderEffects)
        
        return yamlPersistence
    }
    
    private fun createSqlitePersistence(
        maceWielders: MutableMap<UUID, MaceWielder>,
        looseMaces: MutableMap<UUID, LooseMace>,
        pendingMaceRemoval: MutableSet<UUID>,
        startWielderEffects: (Player) -> Unit
    ): MacePersistence {
        // Check if SQLite driver is available
        val driverLoader = SqliteDriverLoader(plugin)
        
        if (!driverLoader.ensureDriver()) {
            logger.warning("[Storage] Failed to load SQLite driver, falling back to YAML")
            return LegendaryMacePersistence(
                plugin, maceWielders, looseMaces, pendingMaceRemoval, startWielderEffects
            )
        }
        
        // Create SQLite persistence
        val sqlitePersistence = SqlitePersistence(
            plugin, maceWielders, looseMaces, pendingMaceRemoval, startWielderEffects
        )
        
        // Check if migration from YAML is needed
        migrateFromYamlIfNeeded(sqlitePersistence, maceWielders, looseMaces, pendingMaceRemoval, startWielderEffects)
        
        return sqlitePersistence
    }
    
    /**
     * Migrate data from YAML to SQLite if YAML data exists and SQLite is empty.
     */
    private fun migrateFromYamlIfNeeded(
        sqlitePersistence: SqlitePersistence,
        maceWielders: MutableMap<UUID, MaceWielder>,
        looseMaces: MutableMap<UUID, LooseMace>,
        pendingMaceRemoval: MutableSet<UUID>,
        startWielderEffects: (Player) -> Unit
    ) {
        val yamlFile = java.io.File(plugin.dataFolder, "mace.yml")
        if (!yamlFile.exists()) {
            logger.info("[Storage] No YAML data to migrate")
            return
        }
        
        // Load SQLite data first to check if it's empty
        sqlitePersistence.loadData()
        val sqliteHasData = maceWielders.isNotEmpty() || looseMaces.isNotEmpty()
        
        if (sqliteHasData) {
            logger.info("[Storage] SQLite already has data, skipping migration")
            return
        }
        
        logger.info("[Storage] Migrating data from YAML to SQLite...")
        
        // Temporarily load YAML data
        val yamlPersistence = LegendaryMacePersistence(
            plugin, maceWielders, looseMaces, pendingMaceRemoval, startWielderEffects
        )
        yamlPersistence.loadData()
        
        val wielderCount = maceWielders.size
        val looseCount = looseMaces.size
        
        if (wielderCount == 0 && looseCount == 0) {
            logger.info("[Storage] No data to migrate from YAML")
            return
        }
        
        // Save to SQLite
        sqlitePersistence.markDirty()
        sqlitePersistence.flushIfDirty(true)
        
        // Rename YAML file to backup
        val backupFile = java.io.File(plugin.dataFolder, "mace.yml.migrated")
        if (yamlFile.renameTo(backupFile)) {
            logger.info("[Storage] Migration complete! Migrated $wielderCount wielders, $looseCount loose maces")
            logger.info("[Storage] Original YAML backed up to mace.yml.migrated")
        } else {
            logger.warning("[Storage] Migration complete but failed to rename YAML file")
        }
    }
    
    /**
     * Migrate data from SQLite to YAML if SQLite data exists and YAML is empty.
     */
    private fun migrateFromSqliteIfNeeded(
        yamlPersistence: LegendaryMacePersistence,
        maceWielders: MutableMap<UUID, MaceWielder>,
        looseMaces: MutableMap<UUID, LooseMace>,
        pendingMaceRemoval: MutableSet<UUID>,
        startWielderEffects: (Player) -> Unit
    ) {
        val sqliteFile = java.io.File(plugin.dataFolder, "mace_data.db")
        if (!sqliteFile.exists()) {
            logger.info("[Storage] No SQLite data to migrate")
            return
        }
        
        // Load YAML data first to check if it's empty
        yamlPersistence.loadData()
        val yamlHasData = maceWielders.isNotEmpty() || looseMaces.isNotEmpty()
        
        if (yamlHasData) {
            logger.info("[Storage] YAML already has data, skipping migration from SQLite")
            return
        }
        
        // Try to load SQLite driver to read data
        val driverLoader = SqliteDriverLoader(plugin)
        if (!driverLoader.ensureDriver()) {
            logger.warning("[Storage] Cannot load SQLite driver for migration, skipping")
            return
        }
        
        logger.info("[Storage] Migrating data from SQLite to YAML...")
        
        // Temporarily load SQLite data
        val sqlitePersistence = SqlitePersistence(
            plugin, maceWielders, looseMaces, pendingMaceRemoval, startWielderEffects
        )
        sqlitePersistence.loadData()
        
        val wielderCount = maceWielders.size
        val looseCount = looseMaces.size
        
        if (wielderCount == 0 && looseCount == 0) {
            logger.info("[Storage] No data to migrate from SQLite")
            sqlitePersistence.close()
            return
        }
        
        // Save to YAML
        yamlPersistence.markDirty()
        yamlPersistence.flushIfDirty(true)
        
        // Close SQLite and rename database to backup
        sqlitePersistence.close()
        val backupFile = java.io.File(plugin.dataFolder, "mace_data.db.migrated")
        if (sqliteFile.renameTo(backupFile)) {
            logger.info("[Storage] Migration complete! Migrated $wielderCount wielders, $looseCount loose maces")
            logger.info("[Storage] Original SQLite backed up to mace_data.db.migrated")
        } else {
            logger.warning("[Storage] Migration complete but failed to rename SQLite file")
        }
    }
}

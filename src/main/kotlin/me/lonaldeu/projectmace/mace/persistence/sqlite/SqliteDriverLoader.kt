package me.lonaldeu.projectmace.mace.persistence.sqlite

import org.bukkit.plugin.Plugin
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.URLClassLoader
import java.security.MessageDigest
import java.util.logging.Logger

/**
 * Downloads and loads SQLite JDBC driver at runtime.
 * This keeps the plugin jar small by not bundling the driver.
 */
internal class SqliteDriverLoader(
    private val plugin: Plugin,
    private val logger: Logger = plugin.logger
) {
    companion object {
        private const val SQLITE_VERSION = "3.45.1.0"
        private const val SQLITE_JAR = "sqlite-jdbc-$SQLITE_VERSION.jar"
        private const val DOWNLOAD_URL = "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/$SQLITE_VERSION/sqlite-jdbc-$SQLITE_VERSION.jar"
        private const val EXPECTED_SHA256 = "7a9f0d4bfa8a4c7e1edfffd8f4b5c20a7f8a3b9c8d5e2f1a0b9c8d7e6f5a4b3c" // Placeholder - update with real hash
        
        @Volatile
        private var driverLoaded = false
    }
    
    private val libsFolder: File = File(plugin.dataFolder, "libs")
    private val sqliteJar: File = File(libsFolder, SQLITE_JAR)
    
    /**
     * Ensures SQLite driver is available, downloading if necessary.
     * @return true if driver is ready, false if download/load failed
     */
    fun ensureDriver(): Boolean {
        if (driverLoaded) return true
        
        synchronized(this) {
            if (driverLoaded) return true
            
            try {
                if (!sqliteJar.exists()) {
                    logger.info("[SQLite] Driver not found, downloading...")
                    if (!downloadDriver()) {
                        return false
                    }
                }
                
                if (!loadDriver()) {
                    return false
                }
                
                driverLoaded = true
                logger.info("[SQLite] Driver loaded successfully")
                return true
                
            } catch (e: Exception) {
                logger.severe("[SQLite] Failed to load driver: ${e.message}")
                e.printStackTrace()
                return false
            }
        }
    }
    
    private fun downloadDriver(): Boolean {
        try {
            if (!libsFolder.exists()) {
                libsFolder.mkdirs()
            }
            
            logger.info("[SQLite] Downloading driver from Maven Central...")
            logger.info("[SQLite] This is a one-time download (~7MB)")
            
            val tempFile = File(libsFolder, "$SQLITE_JAR.tmp")
            
            java.net.URI(DOWNLOAD_URL).toURL().openStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytes = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                    
                    logger.info("[SQLite] Downloaded ${totalBytes / 1024}KB")
                }
            }
            
            // Rename temp file to final name
            if (sqliteJar.exists()) {
                sqliteJar.delete()
            }
            tempFile.renameTo(sqliteJar)
            
            logger.info("[SQLite] Driver saved to ${sqliteJar.absolutePath}")
            return true
            
        } catch (e: Exception) {
            logger.severe("[SQLite] Download failed: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    private fun loadDriver(): Boolean {
        try {
            // Get the plugin's class loader
            val pluginClassLoader = plugin.javaClass.classLoader
            
            // Use URLClassLoader to add the jar to classpath
            val jarUrl = sqliteJar.toURI().toURL()
            
            // Create a new class loader with the SQLite jar
            val loader = URLClassLoader(arrayOf(jarUrl), pluginClassLoader)
            
            // Load the JDBC driver class
            val driverClass = loader.loadClass("org.sqlite.JDBC")
            val driver = driverClass.getDeclaredConstructor().newInstance() as java.sql.Driver
            
            // Register with DriverManager
            java.sql.DriverManager.registerDriver(DriverShim(driver))
            
            logger.info("[SQLite] JDBC driver registered")
            return true
            
        } catch (e: Exception) {
            logger.severe("[SQLite] Failed to load driver class: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Wrapper to prevent DriverManager from unloading our driver
     */
    private class DriverShim(private val driver: java.sql.Driver) : java.sql.Driver by driver {
        override fun connect(url: String?, info: java.util.Properties?): java.sql.Connection? {
            return driver.connect(url, info)
        }
        
        override fun acceptsURL(url: String?): Boolean {
            return driver.acceptsURL(url)
        }
    }
}

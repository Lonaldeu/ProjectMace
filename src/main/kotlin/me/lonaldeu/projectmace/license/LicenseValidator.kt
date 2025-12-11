package me.lonaldeu.projectmace.license

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.bukkit.plugin.java.JavaPlugin
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.logging.Level

object LicenseValidator {

    private val gson = Gson()
    private var cachedHWID: String? = null

    data class ValidationResult(val isValid: Boolean, val secret: String?, val payload: String?)

    fun validate(plugin: JavaPlugin, licenseKey: String, product: String, apiUrl: String): CompletableFuture<ValidationResult> {
        // Use Kotlin coroutines wrapper around CompletableFuture logic or just keep it simple with standard Future
        // For simplicity and compatibility with existing pattern:
        return CompletableFuture.supplyAsync {
            try {
                val url = URL("$apiUrl/api/validate")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", "Java-Plugin-Validator")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val hwid = getHWID()
                val checkSalt = java.util.UUID.randomUUID().toString().replace("-", "")
                val serverIp = getServerIp(plugin)
                val serverPort = plugin.server.port

                val payload = mapOf(
                    "licensekey" to licenseKey,
                    "product" to product,
                    "hwid" to hwid,
                    "check_salt" to checkSalt,
                    "server_ip" to serverIp,
                    "server_port" to serverPort
                )

                val jsonInputString = gson.toJson(payload)

                conn.outputStream.use { os ->
                    val input = jsonInputString.toByteArray(StandardCharsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val responseCode = conn.responseCode

                BufferedReader(InputStreamReader(
                    if (responseCode == 200) conn.inputStream else conn.errorStream,
                    StandardCharsets.UTF_8
                )).use { br ->
                    val response = br.readText()

                    if (responseCode == 200) {
                        val jsonResponse = gson.fromJson(response, JsonObject::class.java)
                        
                        // Handle case where status might be missing or different
                        if (jsonResponse.has("status") && jsonResponse.get("status").asString == "success") {
                            val remoteSalt = jsonResponse.get("remote_salt").asString
                            val confirmationHash = jsonResponse.get("confirmation_hash").asString
                            
                            val expectedHash = sha256(remoteSalt + checkSalt)
                            
                            if (expectedHash == confirmationHash) {
                                val secret = if (jsonResponse.has("secret")) jsonResponse.get("secret").asString else null
                                val payloadData = if (jsonResponse.has("payload")) jsonResponse.get("payload").asString else null
                                return@supplyAsync ValidationResult(true, secret, payloadData)
                            } else {
                                plugin.logger.severe("!!! SECURITY CHECK FAILED !!!")
                                plugin.logger.severe("The validation server's response hash is invalid.")
                                plugin.logger.severe("This could be a man-in-the-middle attack.")
                                return@supplyAsync ValidationResult(false, null, null)
                            }
                        } else {
                            plugin.logger.warning("Validation API returned an unexpected success response.")
                            return@supplyAsync ValidationResult(false, null, null)
                        }
                    } else {
                         var errorMessage = "Unknown validation error."
                         try {
                             val errorResponse = gson.fromJson(response, JsonObject::class.java)
                             if (errorResponse.has("message")) {
                                 errorMessage = errorResponse.get("message").asString
                             }
                         } catch (e: Exception) {
                             // Ignore
                         }
                         plugin.logger.warning("License check failed ($responseCode): $errorMessage")
                         return@supplyAsync ValidationResult(false, null, null)
                    }
                }
            } catch (e: Exception) {
                plugin.logger.log(Level.SEVERE, "Could not contact validation server.", e)
                throw RuntimeException("API connection failed. " + e.message, e)
            }
        }
    }

    private fun sha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            hexString.toString()
        } catch (e: Exception) {
            throw RuntimeException("SHA-256 hashing failed", e)
        }
    }

    private fun getHWID(): String {
        cachedHWID?.let { return it }
        return try {
            val toHash = (System.getProperty("os.name") +
                    System.getProperty("os.arch") +
                    System.getProperty("os.version") +
                    Runtime.getRuntime().availableProcessors() +
                    System.getenv("PROCESSOR_IDENTIFIER") +
                    System.getenv("PROCESSOR_ARCHITECTURE") +
                    System.getenv("PROCESSOR_LEVEL") +
                    System.getenv("COMPUTERNAME"))
            sha256(toHash).also { cachedHWID = it }
        } catch (e: Exception) {
            "unknown-hwid"
        }
    }

    private fun getServerIp(plugin: JavaPlugin): String {
        return try {
            val serverIp = plugin.server.ip
            if (serverIp.isNotEmpty() && serverIp != "0.0.0.0") {
                return serverIp
            }

            try {
                val url = URL("https://api.ipify.org")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.inputStream.bufferedReader().use { it.readLine() } ?: "unknown"
            } catch (ignored: Exception) {
                 try {
                     java.net.InetAddress.getLocalHost().hostAddress
                 } catch (e: Exception) {
                     "unknown"
                 }
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
}

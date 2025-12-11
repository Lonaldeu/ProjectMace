package me.lonaldeu.projectmace.license

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles runtime decryption of strings using the secret key provided by the license server.
 * If the key is missing or invalid, decryption fails.
 */
object StringVault {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12 // 96 bits
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val KEY_ALGORITHM = "AES"

    private var secretKey: SecretKey? = null
    private val strings = ConcurrentHashMap<String, String>()

    init {
        // Permissions
        strings["PERM_RELOAD"] = "1DCe36hsdVe5ClNPO6oyhjKdvjFeZ7KRv4joDi0IAHto1P9Y2iaC"
        strings["PERM_UNCLAIM"] = "IJvk/Nb5TunuM+ND1axAQozlUrH1cHqDrIwxl9nDAhjUrtPdPYTJFw=="
        strings["PERM_SEARCH"] = "qvtw71CZ98iBptWLQGrt13rOdH0boR+6Ab3B1Ip4MalPeGHmBwbW"
        
        // Messages
        strings["MSG_RELOAD_SUCCESS"] = "w/Ob5CT7/yOZ+jbp0xUa3D74kkYzNwRXoPhO9QkbhTgJj94voQAmiMs8BIcVwr6c4Iu1PSvL1Jt49JftyqzXPS8C"
        strings["MSG_LICENSE_FAIL"] = "c5OrcdjZA7dnVXkSRa6Mu0oxXk5e1WvwX55QpJVRKFak/2YEGhWFg4ISJxWgEqOuu/P+y21vFB2bwqNcQkCMKlE+oFtN6qJ/5l9XiWNr516MixqfWBgdlH1b"
        
        // NBT Keys
        strings["NBT_MACE_UUID"] = "ZSyS/zeTOB4yXy8v1b+/e0PsNRxxxCnrC4La4qfa4uZxL6lGEJh6mmJ/b1fD4B4="
        strings["NBT_MAX_DURABILITY"] = "QqM5dCw1bUeLM3cknjiODDCSYHdY5CaR64N36EsvEp+/b2Zs31in4I/85L/NIHxrQ1h0MAX6v+Du"
        strings["NBT_LORE_DURABILITY"] = "Ol3shBkmYnOArBsl7ITvkL/cmhBlbkCioyEro+rBoz7SOFYZluZNL1agYldA6ogXE0KL/i+AbPFHEJM7zw=="
        
        // Critical Config Paths
        strings["CFG_BASE_DAMAGE"] = "F9rkSfgJr3rHTcX6ozG/gpAIiCD+LHSE/Xx5YG0r2NH6fdwLgIAn5RikaqQPqN+OczZyWlX20yiecCNhh0HS"
        strings["CFG_DAMAGE_MULT"] = "W6db5HW+rcX6En2YAd4eJVdw3/OTWAIoV3JzwVkbE1gCxrGJeEXIoeaqpJOAsfYoZsJzPpWI1NOyzV+oBNQSbqtAQUGs"
        strings["CFG_MAX_MACES"] = "j44i20v+3TXXnh2L00pNoxO3f3oPJK3cROGVV/9tkx4SGQ/yXNetNHL6zhBbqaI="

        // Database Tables
        strings["DB_TABLE_WIELDERS"] = "sAPVewsmkFlyU11qt5H6Us9mKLWmYfR9u+3ApFYCL1SqLGcoUL/Hwgk="
        strings["DB_TABLE_LOOSE"] = "Qv/CkmOXbDkzCEJwb+k96oa+9GqBW61k7XP8mLlHRRcTdXDwOI1i"
        strings["DB_TABLE_PENDING"] = "Bgn6+pdm8K3HucVOkBZNE1n5XWo2zqc/ji9tTUZVlemxCABRfJUcQRdajwrJFBzc"
    }

    /**
     * Initializes the vault with the secret key from the license server.
     * @param keyHex The 32-character hex string key.
     */
    fun init(keyHex: String?) {
        require(!(keyHex == null || keyHex.length != 32)) { "Invalid license secret key." }
        secretKey = SecretKeySpec(keyHex.toByteArray(StandardCharsets.UTF_8), KEY_ALGORITHM)
    }

    /**
     * Retrieves a decrypted string.
     */
    fun get(key: String): String {
        if (secretKey == null) {
            throw IllegalStateException("StringVault not initialized! License check likely failed or bypassed.")
        }

        val encrypted = strings[key] ?: return "MISSING_STRING_$key"

        return try {
            decrypt(encrypted)
        } catch (e: Exception) {
            "DECRYPTION_FAILED_$key"
        }
    }

    @Throws(Exception::class)
    private fun decrypt(encryptedText: String): String {
        val decode = Base64.getDecoder().decode(encryptedText)

        val iv = ByteArray(GCM_IV_LENGTH)
        System.arraycopy(decode, 0, iv, 0, GCM_IV_LENGTH)

        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        val cipherText = ByteArray(decode.size - GCM_IV_LENGTH)
        System.arraycopy(decode, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

        val plainText = cipher.doFinal(cipherText)
        return String(plainText, StandardCharsets.UTF_8)
    }

    /**
     * Helper to add strings dynamically.
     */
    fun add(key: String, value: String) {
        strings[key] = value
    }
}

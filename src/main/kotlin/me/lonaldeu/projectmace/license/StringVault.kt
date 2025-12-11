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
        strings["PERM_RELOAD"] = "Aanyy9hjPs6MZbP5/cjLwjRlXQgINFjogPs9fXLVGGkqr0i00umY"
        strings["PERM_UNCLAIM"] = "Vw5285Ien9Bp+iK2B0VHiU7CMNWL6y5qyg6PA8b/Y0inoIhq5T46LQ=="
        strings["PERM_SEARCH"] = "9l7eyXDIdDg2y276jEM8BcusRtRJfLsnilBFSDcJauvGAG3Ox8zc"

        // Messages
        strings["MSG_RELOAD_SUCCESS"] = "IIQ2Qs9Q7C8y/HUyWi8zZZGv2fuWDURq6+ZlvHhFhPTS7cUlZVOi0VZV3VaUbnpYtaC0nWCqj/bcuGsLEC+TjanH"
        strings["MSG_LICENSE_FAIL"] = "z0j//2JZm2P0cde5fPwcAjmbTuAzgKg7iE0o2BhOGQaZHXrYrCy7sRI/AOfsDEjj0kvg4zxfFpZiGxkmkj7agOqxJ0fjnazQPArEV3Q11Or01Ns0hDd1RMRW"

        // NBT Keys (Protecting internal identifiers)
        strings["NBT_MACE_UUID"] = "YMqAFFjQtQsjI34pDZaYYYP3SiwLQqvG9wDVqEXUCyhp8/tJP3BW2/DL0PfPcwg="
        strings["NBT_MAX_DURABILITY"] = "bds/ry5425waRTsctXGIruUw0hZNwfxANHeP//7pNAb3hC0OF6LJ3G1M0X5cPSA1pnmLMrkuTlCK"
        strings["NBT_LORE_DURABILITY"] = "hEe1opoDID9xlKiOHzD+Lcq6NNFU9YU7Z8bz5wdwIpfBTqqAWYLnNLcRVensIzdSmAR5u5HlN9V6SRLWFw=="

        // Critical Configuration Paths
        strings["CFG_BASE_DAMAGE"] = "nqV9gUEY6h4ZXXlF/Z8d2s+ezgsUT4gSmfLLa8ZQdRIWi18PMLu5rrZtRFd6U5xxCPHz8d/MABXd6z2ogYrq"
        strings["CFG_DAMAGE_MULT"] = "MJCbzMyaijsmxbJ8ve6qKftP5C+nSml38kpCoiLDImtZQvxqsgJTFwhuLAmlUqrtmaF06+yx7eeQN9fpsEUC2ZFnDBDO"
        strings["CFG_MAX_MACES"] = "ACR/A/2iDRmD0n+ZtvwkJTjns7IBRU1GfS7QiROu6f+bvHBtQxlJQo5P/yCnTg=="
        
        // Database Table Names (Schema protection)
        strings["DB_TABLE_WIELDERS"] = "mBoSxGiHbDyRtnq9KD91OzC33DO+FH3ZsFFv94c/WmC4Wmd1ABtG19M="
        strings["DB_TABLE_LOOSE"] = "/JiUePy8JVt5rdyXO2JhnHLtMqlN/ZFYYkRcff1WxC5GfAbWNiac"
        strings["DB_TABLE_PENDING"] = "Fz3ueTKmiYZlqVejaf9WAB6EVjAjcrsfXtXUuBCxGJb8ffobqBavjv/NIIySiPhb"
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

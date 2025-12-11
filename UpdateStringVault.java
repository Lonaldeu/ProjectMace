import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class UpdateStringVault {

    private static final Map<String, String> STRINGS_TO_ENCRYPT = new HashMap<>();

    static {
        STRINGS_TO_ENCRYPT.put("PERM_RELOAD", "mace.reload");
        STRINGS_TO_ENCRYPT.put("PERM_UNCLAIM", "mace.unclaim");
        STRINGS_TO_ENCRYPT.put("PERM_SEARCH", "mace.search");
        STRINGS_TO_ENCRYPT.put("MSG_RELOAD_SUCCESS", "&aConfiguration reloaded successfully!");
        STRINGS_TO_ENCRYPT.put("MSG_LICENSE_FAIL", "&cLicense validation failed! Plugin functionality is disabled.");

        // NBT Keys
        STRINGS_TO_ENCRYPT.put("NBT_MACE_UUID", "legendary_mace_uuid");
        STRINGS_TO_ENCRYPT.put("NBT_MAX_DURABILITY", "legendary_mace_max_durability");
        STRINGS_TO_ENCRYPT.put("NBT_LORE_DURABILITY", "legendary_mace_current_durability");

        // Critical Config Paths
        STRINGS_TO_ENCRYPT.put("CFG_BASE_DAMAGE", "gameplay.combat.scoring.base-damage");
        STRINGS_TO_ENCRYPT.put("CFG_DAMAGE_MULT", "gameplay.combat.scoring.damage-multiplier");
        STRINGS_TO_ENCRYPT.put("CFG_MAX_MACES", "gameplay.max-maces");

        // Database Tables
        STRINGS_TO_ENCRYPT.put("DB_TABLE_WIELDERS", "mace_wielders");
        STRINGS_TO_ENCRYPT.put("DB_TABLE_LOOSE", "loose_maces");
        STRINGS_TO_ENCRYPT.put("DB_TABLE_PENDING", "pending_mace_removal");
    }

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    public static void main(String[] args) throws Exception {
        byte[] keyBytes = new byte[16];
        new SecureRandom().nextBytes(keyBytes);
        String generatedKeyHex = bytesToHex(keyBytes);

        try (java.io.FileWriter writer = new java.io.FileWriter("keys.txt", java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write("KEY:" + generatedKeyHex + "\n");

            SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
            for (Map.Entry<String, String> entry : STRINGS_TO_ENCRYPT.entrySet()) {
                String encrypted = encrypt(entry.getValue(), secretKey);
                writer.write(entry.getKey() + ":" + encrypted + "\n");
            }
        }
        System.out.println("Done.");
    }

    private static String encrypt(String plaintext, SecretKey key) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

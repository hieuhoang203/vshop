package vn.vshop.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Application-level encryption for personal data stored in PostgreSQL (phone, street address).
 *
 * <p>Envelope: {@code v1.<keyId>.<base64url(iv || ciphertext+tag)>}, AES-256-GCM with a random
 * 96-bit IV per value. The associated data binds each ciphertext to its table, column and row, so a
 * value copied to another row or column fails to decrypt. Keys come from configuration ({@code
 * vshop.pii.keys.<id>} as base64 32-byte keys, {@code vshop.pii.active-key} for new writes); old
 * key ids stay readable, which allows rotation. Keys are not stored in the database. This protects
 * database dumps and read-only database access; it does not protect against someone who controls
 * the running application and its configuration.
 */
@Component
public class PiiCipher {
    private static final String VERSION = "v1";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Map<String, byte[]> keys;
    private final String activeKeyId;
    private final SecureRandom random = new SecureRandom();

    public PiiCipher(Environment environment) {
        Binder binder = Binder.get(environment);
        Map<String, String> configured =
                binder.bind("vshop.pii.keys", Bindable.mapOf(String.class, String.class))
                        .orElse(Map.of());
        this.keys =
                configured.entrySet().stream()
                        // A key placeholder with no value means "not configured", not an empty key.
                        .filter(entry -> !entry.getValue().isBlank())
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        Map.Entry::getKey, entry -> decodeKey(entry)));
        this.activeKeyId = binder.bind("vshop.pii.active-key", String.class).orElse("");
        if (!keys.containsKey(activeKeyId)) {
            LoggerFactory.getLogger(PiiCipher.class)
                    .warn(
                            "PII encryption key is not configured; features that store personal data will fail until it is.");
        }
    }

    private static byte[] decodeKey(Map.Entry<String, String> entry) {
        byte[] key = Base64.getDecoder().decode(entry.getValue().strip());
        if (key.length != 32) {
            throw new IllegalStateException(
                    "PII key '" + entry.getKey() + "' must be 32 bytes (base64).");
        }
        return key;
    }

    public String encrypt(String plaintext, String context) {
        byte[] key = keys.get(activeKeyId);
        if (key == null) {
            throw new IllegalStateException(
                    "PII encryption is not configured: set vshop.pii.keys.<id> and"
                            + " vshop.pii.active-key (run scripts/init-local.ps1).");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer out = ByteBuffer.allocate(iv.length + sealed.length);
            out.put(iv).put(sealed);
            return VERSION
                    + "."
                    + activeKeyId
                    + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(out.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt personal data", e);
        }
    }

    public String decrypt(String envelope, String context) {
        String[] parts = envelope.split("\\.", 3);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new IllegalStateException("Unrecognised encrypted value format");
        }
        byte[] key = keys.get(parts[1]);
        if (key == null) {
            throw new IllegalStateException("No key available for id '" + parts[1] + "'");
        }
        try {
            byte[] raw = Base64.getUrlDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(
                    cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Wrong key, tampered value or wrong row/column binding: never return partial data.
            throw new IllegalStateException("Could not decrypt personal data", e);
        }
    }
}

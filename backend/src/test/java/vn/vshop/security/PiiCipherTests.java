package vn.vshop.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import vn.vshop.common.security.PiiCipher;

class PiiCipherTests {
    private static final String KEY_1 =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String KEY_2 =
            Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    private static PiiCipher cipher(String active, String... keyPairs) {
        MockEnvironment env = new MockEnvironment().withProperty("vshop.pii.active-key", active);
        for (int i = 0; i < keyPairs.length; i += 2) {
            env.withProperty("vshop.pii.keys." + keyPairs[i], keyPairs[i + 1]);
        }
        return new PiiCipher(env);
    }

    @Test
    void roundTripsUnicodeAndNeverRepeatsACiphertext() {
        PiiCipher cipher = cipher("k1", "k1", KEY_1);
        String text = "Số 12, ngõ 34 Nguyễn Trãi, Hà Nội";
        String first = cipher.encrypt(text, "ctx");
        String second = cipher.encrypt(text, "ctx");
        assertThat(cipher.decrypt(first, "ctx")).isEqualTo(text);
        assertThat(first).startsWith("v1.k1.").isNotEqualTo(second); // fresh IV every time
        assertThat(first).doesNotContain("Nguyễn").doesNotContain("Hà Nội");
    }

    @Test
    void aValueIsBoundToItsRowAndColumn() {
        PiiCipher cipher = cipher("k1", "k1", KEY_1);
        String sealed = cipher.encrypt("0912345678", "identity.address.phone:row-a");
        assertThatThrownBy(() -> cipher.decrypt(sealed, "identity.address.phone:row-b"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(sealed, "identity.address.street:row-a"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tamperedOrMalformedValuesFailInsteadOfReturningData() {
        PiiCipher cipher = cipher("k1", "k1", KEY_1);
        String sealed = cipher.encrypt("secret", "ctx");
        String flipped =
                sealed.substring(0, sealed.length() - 2) + (sealed.endsWith("AA") ? "BB" : "AA");
        assertThatThrownBy(() -> cipher.decrypt(flipped, "ctx"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("plaintext", "ctx"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("v2.k1.AAAA", "ctx"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keysCanBeRotatedWithoutLosingOldData() {
        String old = cipher("k1", "k1", KEY_1).encrypt("0912345678", "ctx");
        PiiCipher rotated = cipher("k2", "k1", KEY_1, "k2", KEY_2);
        assertThat(rotated.decrypt(old, "ctx")).isEqualTo("0912345678");
        assertThat(rotated.encrypt("x", "ctx")).startsWith("v1.k2.");
        // Without the old key the old value is unreadable, and that is reported, not hidden.
        assertThatThrownBy(() -> cipher("k2", "k2", KEY_2).decrypt(old, "ctx"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("k1");
    }

    @Test
    void missingOrBadKeysFailLoudlyAndNeverFallBackToPlaintext() {
        assertThatThrownBy(() -> cipher("", "k1", KEY_1).encrypt("x", "ctx"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
        assertThatThrownBy(() -> cipher("k1").encrypt("x", "ctx"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                cipher(
                                        "short",
                                        "short",
                                        Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}

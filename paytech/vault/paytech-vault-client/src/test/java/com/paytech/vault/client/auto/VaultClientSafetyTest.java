package com.paytech.vault.client.auto;

import com.paytech.foundation.common.error.FailException;
import com.paytech.vault.client.*;
import com.paytech.vault.client.internal.wire.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class VaultClientSafetyTest {
    static final String TOKEN = "CANARY_TOKEN_" + "A".repeat(30);
    static final String VALUE = "CANARY_VALUE@example.test";
    static final String KEY = "CANARY_IDEMPOTENCY";

    @Test
    void test_sensitiveObjectsAreRedacted() {
        Object[] values = {new ProtectCommand("EMAIL_ADDRESS_V1", TokenMode.STABLE, VALUE, "CHECKOUT_CONTACT", KEY),
                new ProtectResult(TOKEN), new RevealQuery(TOKEN, "CHECKOUT_CONTACT"), new RevealResult(VALUE),
                new ProtectRequest("EMAIL_ADDRESS_V1", "STABLE", VALUE, "CHECKOUT_CONTACT"),
                new ProtectResponse(TOKEN), new RevealRequest(TOKEN, "CHECKOUT_CONTACT"), new RevealResponse(VALUE),
                new ErrorResponse("INVALID_REQUEST", VALUE, KEY, TOKEN),
                new com.paytech.vault.client.autoconfigure.VaultClientProperties(VALUE, TOKEN, null, null, KEY, false)};
        for (Object value : values) {
            assertThat(value.toString()).doesNotContain(TOKEN, VALUE, KEY);
        }
    }

    @Test
    void test_invalidParameterNeverLeaksOrNormalizes() {
        assertThatThrownBy(() -> new RevealQuery(TOKEN + " ", "CHECKOUT_CONTACT"))
                .isInstanceOf(FailException.class).hasMessageNotContaining(TOKEN).hasNoCause();
        for (String bad : new String[]{null, "", " ", "lowercase", "A".repeat(65), "A\n"}) {
            assertThatThrownBy(() -> new ProtectCommand(bad, TokenMode.STABLE, VALUE, "CHECKOUT_CONTACT", KEY))
                    .isInstanceOf(FailException.class).hasNoCause();
        }
        for (String bad : new String[]{null, "", " ", "a,b", "a\r\nb", "a".repeat(129)}) {
            assertThatThrownBy(() -> new ProtectCommand("EMAIL_ADDRESS_V1", TokenMode.STABLE, VALUE, "P", bad))
                    .isInstanceOf(FailException.class).hasNoCause();
        }
        assertThatThrownBy(() -> new ProtectCommand("A", null, VALUE, "P", KEY)).isInstanceOf(FailException.class);
        assertThatThrownBy(() -> new ProtectCommand("A", TokenMode.STABLE, null, "P", KEY)).isInstanceOf(FailException.class);
        assertThatThrownBy(() -> new RevealQuery(TOKEN, null)).isInstanceOf(FailException.class);
        assertThatThrownBy(() -> new RevealQuery(null, "P")).isInstanceOf(FailException.class);
    }

    @Test
    void test_utf8LimitAndSurrogates() {
        for (String bad : new String[]{"\uD800", "\uDC00", "x\uD800y", "é".repeat(16385)}) {
            assertThatThrownBy(() -> new ProtectCommand("A", TokenMode.STABLE, bad, "P", KEY))
                    .isInstanceOf(FailException.class).hasNoCause();
        }
        for (String valid : new String[]{"", "  É\r\n", "é".repeat(16384), "😀", "�"}) {
            assertThat(new ProtectCommand("A", TokenMode.STABLE, valid, "P", KEY).value()).isEqualTo(valid);
        }
    }
}

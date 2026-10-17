package com.paytech.payment.auto.card;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.payment.card.core.configuration.PaymentCardProperties;

/**
 * 卡指纹密钥的启动校验（需求 AC-07）：缺失、不是 Base64 或解码后不足 32 字节时启动失败；错误信息不含密钥值。
 */
class PaymentCardPropertiesTest {
    /** 无效配置使属性初始化失败。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not base64 ***", "c2hvcnQta2V5"})
    void test_invalidFingerprintKeyFailsStartup(String fingerprintKey) {
        PaymentCardProperties properties = new PaymentCardProperties();
        properties.setFingerprintKey(fingerprintKey);

        var failure = assertThrows(IllegalArgumentException.class, properties::afterPropertiesSet);

        assertTrue(failure.getMessage().startsWith("payment.card.fingerprint-key"));
    }

    /** 32 字节的 Base64 密钥通过，且 toString 不含密钥。 */
    @Test
    void test_validFingerprintKey() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        PaymentCardProperties properties = new PaymentCardProperties();
        properties.setFingerprintKey(key);

        properties.afterPropertiesSet();

        assertAll(() -> assertEquals(32, properties.fingerprintKeyBytes().length),
            () -> assertFalse(properties.toString().contains(key)));
    }
}

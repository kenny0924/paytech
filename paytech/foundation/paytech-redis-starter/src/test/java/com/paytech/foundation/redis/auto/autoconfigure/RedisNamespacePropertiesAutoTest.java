package com.paytech.foundation.redis.auto.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.autoconfigure.RedisNamespaceProperties;
import com.paytech.foundation.redis.core.error.RedisErrorCode;

class RedisNamespacePropertiesAutoTest {

    @Test
    void defaultsKeyPrefixToApplicationName() {
        RedisNamespaceProperties properties = new RedisNamespaceProperties();

        assertEquals("payment-service", properties.resolveKeyPrefix("payment-service"));
    }

    @Test
    void acceptsExplicitKeyPrefixOverride() {
        RedisNamespaceProperties properties = new RedisNamespaceProperties();
        properties.setKeyPrefix("payment-service:test");

        assertEquals("payment-service:test", properties.resolveKeyPrefix("ignored-service"));
    }

    @Test
    void rejectsInvalidExplicitKeyPrefix() {
        RedisNamespaceProperties properties = new RedisNamespaceProperties();
        properties.setKeyPrefix("Payment_Service");

        FailException exception =
            assertThrows(FailException.class, () -> properties.resolveKeyPrefix("payment-service"));

        assertEquals(RedisErrorCode.CONFIGURATION_INVALID.getCode(), exception.getCode());
    }

    @Test
    void rejectsMissingKeyPrefixAndApplicationName() {
        RedisNamespaceProperties properties = new RedisNamespaceProperties();

        assertThrows(FailException.class, () -> properties.resolveKeyPrefix(null));
    }
}

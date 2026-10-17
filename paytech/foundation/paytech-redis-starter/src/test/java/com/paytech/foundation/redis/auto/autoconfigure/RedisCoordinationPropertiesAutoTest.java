package com.paytech.foundation.redis.auto.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.autoconfigure.RedisCoordinationProperties;

class RedisCoordinationPropertiesAutoTest {

    @Test
    void rejectsMinimumIdleSizeGreaterThanPoolSize() {
        RedisCoordinationProperties properties = validProperties();
        properties.setConnectionMinimumIdleSize(17);
        properties.setConnectionPoolSize(16);

        assertThrows(FailException.class, properties::validateEnabledConfiguration);
    }

    private RedisCoordinationProperties validProperties() {
        RedisCoordinationProperties properties = new RedisCoordinationProperties();
        properties.setEnabled(true);
        return properties;
    }
}

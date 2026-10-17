package com.paytech.foundation.redis.test.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Per-JVM unpredictable Redis namespace with SCAN-only cleanup. */
public final class RedisTestNamespace {

    private final String value;

    private RedisTestNamespace(String value) {
        this.value = value;
    }

    public static RedisTestNamespace unique(String purpose) {
        return new RedisTestNamespace("redis-starter-" + purpose + "-" + UUID.randomUUID());
    }

    public String value() {
        return value;
    }

    public String physical(String logicalKey) {
        return value + ":" + logicalKey;
    }

    public void clean(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate raw = new StringRedisTemplate(connectionFactory);
        ScanOptions options = ScanOptions.scanOptions().match(value + ":*").count(100).build();
        var batch = new ArrayList<String>();
        try (Cursor<String> cursor = raw.scan(options)) {
            cursor.forEachRemaining(batch::add);
        }
        if (!batch.isEmpty()) {
            raw.delete(List.copyOf(batch));
        }
    }
}

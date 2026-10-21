package com.paytech.reconciliation.test.clear;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.paytech.foundation.testsupport.api.Clear;

/** 清理 mock 环境专属 Redis 命名空间（写入口资源锁）下的全部 Key；只按 SCAN 匹配前缀删除，不触碰其他环境。 */
@TestComponent
public class RedisNamespaceCleaner implements Clear {
    private static final int SCAN_BATCH = 100;

    private final StringRedisTemplate redis;

    private final String keyPattern;

    /** 以测试命名空间前缀构造。 */
    public RedisNamespaceCleaner(RedisConnectionFactory connectionFactory,
        @Value("${redis.key-prefix}") String keyPrefix) {
        this.redis = new StringRedisTemplate(connectionFactory);
        this.keyPattern = keyPrefix + ":*";
    }

    /** 删除本测试命名空间的全部 Key。 */
    @Override
    public void clear() {
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(keyPattern).count(SCAN_BATCH).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}

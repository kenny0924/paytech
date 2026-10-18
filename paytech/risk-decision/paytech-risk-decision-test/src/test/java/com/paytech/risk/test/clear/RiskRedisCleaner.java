package com.paytech.risk.test.clear;

import com.paytech.foundation.testsupport.api.Clear;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 清理 mock 环境专属 Redis 命名空间下的全部 Key（锁、协调状态等）。
 * <li>只按 SCAN 匹配 {@code redis.key-prefix} 前缀删除，不触碰其他环境或开发数据。</li>
 *
 * @author Kenny
 */
@TestComponent
public class RiskRedisCleaner implements Clear {

    private static final int SCAN_BATCH = 100;

    private final StringRedisTemplate redis;

    private final String keyPattern;

    public RiskRedisCleaner(RedisConnectionFactory connectionFactory, @Value("${redis.key-prefix}") String keyPrefix) {
        this.redis = new StringRedisTemplate(connectionFactory);
        this.keyPattern = keyPrefix + ":*";
    }

    @Override
    public void clear() {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(keyPattern).count(SCAN_BATCH).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}

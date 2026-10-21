package com.paytech.ledger.app.configuration;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.paytech.ledger.integration.platform.CurrencySpecSnapshot;

import lombok.AllArgsConstructor;

/**
 * 就绪探针组件 currencySnapshot：首次成功加载 Platform 币种规则快照之前为 DOWN，避免带空快照接流量（Q3）
 * <li>加载后刷新失败仍为 UP（继续使用上一份快照），详情给出加载时间与连续失败次数。</li>
 */
@Component("currencySnapshot")
@AllArgsConstructor
public class CurrencySnapshotHealthIndicator implements HealthIndicator {
    private final CurrencySpecSnapshot snapshot;

    /** 按快照是否已加载给出状态。 */
    @Override
    public Health health() {
        Health.Builder health = snapshot.isLoaded() ? Health.up() : Health.down();
        health.withDetail("consecutiveFailures", snapshot.getConsecutiveFailures());
        if (snapshot.getLoadedAt() != null) {
            health.withDetail("loadedAt", snapshot.getLoadedAt().toString());
        }
        return health.build();
    }
}

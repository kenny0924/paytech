package com.paytech.billing.app.configuration;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.paytech.billing.core.common.CurrencySpecSnapshot;

import lombok.AllArgsConstructor;

/**
 * Platform 币种快照就绪探针（readiness 组成员 {@code currencySnapshot}，Q3）
 * <li>首次加载成功前为 DOWN，避免带空快照接流量；之后刷新失败仍为 UP，由上一份快照继续服务。</li>
 * <li>详情含最近成功加载时间与连续失败次数；是否对外显示由 management.endpoint.health.show-details 决定。</li>
 */
@Component
@AllArgsConstructor
public class CurrencySnapshotHealthIndicator implements HealthIndicator {
    private final CurrencySpecSnapshot snapshot;

    /** 只读内存快照状态。 */
    @Override
    public Health health() {
        Health.Builder builder = snapshot.loaded() ? Health.up().withDetail("loadedAt", snapshot.loadedAt().toString())
            : Health.down();
        return builder.withDetail("consecutiveFailures", snapshot.consecutiveFailures()).build();
    }
}

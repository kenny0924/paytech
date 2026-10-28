package com.paytech.platform.support.referencedata.client.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.paytech.platform.support.referencedata.client.ReferenceDataKind;

/**
 * 健康指标 {@code referenceData}：有已开启种类尚未首次加载成功时为 OUT_OF_SERVICE，全部成功过为 UP（之后刷新失败不改变状态）。
 * <li>明细按种类给出 count、loadedAt、consecutiveFailures、lastFailure（只有异常类名，不带响应体）。</li>
 * <li>消费方把它加入 readiness 组：{@code include: readinessState,referenceData}。</li>
 */
public final class ReferenceDataHealthIndicator implements HealthIndicator {

    private final ReferenceDataSnapshot snapshot;

    /**
     * 读取快照状态
     *
     * @param snapshot 快照
     */
    public ReferenceDataHealthIndicator(ReferenceDataSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    /** 按是否全部加载给出状态，并附各种类明细。 */
    @Override
    public Health health() {
        Health.Builder health = snapshot.allLoaded() ? Health.up() : Health.outOfService();
        for (ReferenceDataKind kind : snapshot.enabledKinds()) {
            health.withDetail(kind.getCode(), details(snapshot.status(kind)));
        }
        return health.build();
    }

    /** 一个种类的明细，空值不输出。 */
    private static Map<String, Object> details(ReferenceDataSnapshot.KindStatus status) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("count", status.count());
        if (status.loadedAt() != null) {
            details.put("loadedAt", status.loadedAt().toString());
        }
        details.put("consecutiveFailures", status.consecutiveFailures());
        if (status.lastFailure() != null) {
            details.put("lastFailure", status.lastFailure());
        }
        return details;
    }
}

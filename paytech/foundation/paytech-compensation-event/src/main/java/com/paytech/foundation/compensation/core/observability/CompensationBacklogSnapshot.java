package com.paytech.foundation.compensation.core.observability;

import java.time.Duration;
import java.util.Objects;

/**
 * 一次兜底扫描采集的服务内积压快照。
 *
 * @param pendingRows
 *            尚未达到成功终态的记录数
 * @param oldestPendingAge
 *            最早保留记录的等待时长
 * @param expiredLeaseRows
 *            Lease 已过期的 PROCESSING 记录数
 * @author Codex
 */
public record CompensationBacklogSnapshot(long pendingRows, Duration oldestPendingAge, long expiredLeaseRows) {

    public CompensationBacklogSnapshot {
        if (pendingRows < 0L) {
            throw new IllegalArgumentException("pendingRows must not be negative");
        }
        oldestPendingAge = Objects.requireNonNull(oldestPendingAge, "oldestPendingAge must not be null");
        if (oldestPendingAge.isNegative()) {
            throw new IllegalArgumentException("oldestPendingAge must not be negative");
        }
        if (expiredLeaseRows < 0L) {
            throw new IllegalArgumentException("expiredLeaseRows must not be negative");
        }
    }
}

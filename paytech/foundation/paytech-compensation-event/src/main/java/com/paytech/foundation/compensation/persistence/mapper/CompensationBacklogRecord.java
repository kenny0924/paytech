package com.paytech.foundation.compensation.persistence.mapper;

/**
 * 一次补偿积压观测对应的数据库聚合值。
 *
 * @param pendingRows
 *            当前保留的事件行数
 * @param oldestPendingMicros
 *            最早待处理事件的等待微秒数
 * @param expiredLeaseRows
 *            租约已过期的 PROCESSING 事件行数
 * @author Codex
 */
public record CompensationBacklogRecord(long pendingRows, long oldestPendingMicros, long expiredLeaseRows) {
}

package com.paytech.foundation.compensation.api;

/**
 * 与人工重开在同一事务中记录的宿主审计事实。
 *
 * @author Codex
 */
public record CompensationRecoveryAudit(long eventId, String eventKey, String eventType, String subEventType,
    String actor, String reason, String ticket, int previousExecuteCount, long previousLockVersion,
    long newLockVersion, String previousErrorCode, String previousErrorSummary) {
}

package com.paytech.foundation.compensation.model;

import java.time.LocalDateTime;

/**
 * 完整的持久化补偿事件记录。
 *
 * @author Codex
 */
public record CompensationEventRecord(long id, String eventKey, String eventType, String subEventType,
    String payloadJson, CompensationEventStatus executeStatus, int executeCount, LocalDateTime nextExecuteAt,
    String lockOwner, LocalDateTime lockUntil, String lastErrorCode, String lastErrorSummary, long lockVersion,
    LocalDateTime createTime, LocalDateTime updateTime) {
}

package com.paytech.foundation.compensation.api;

/**
 * 对一条最终失败的本地事件执行人工重开的审计请求。
 *
 * @param eventId
 *            失败事件标识
 * @param expectedLockVersion
 *            恢复操作期望的乐观锁版本
 * @param actor
 *            已认证操作人身份
 * @param reason
 *            已审批的恢复原因
 * @param ticket
 *            外部运维或事故单引用
 * @author Codex
 */
public record CompensationEventReopenRequest(long eventId, long expectedLockVersion, String actor, String reason,
    String ticket) {

    public CompensationEventReopenRequest {
        if (eventId <= 0L) {
            throw new IllegalArgumentException("eventId must be positive");
        }
        if (expectedLockVersion <= 0L) {
            throw new IllegalArgumentException("expectedLockVersion must be positive");
        }
        actor = requireText("actor", actor, 128);
        reason = requireText("reason", reason, 500);
        ticket = requireText("ticket", ticket, 128);
    }

    private static String requireText(String field, String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maximumLength) {
            throw new IllegalArgumentException(field + " exceeds " + maximumLength + " characters");
        }
        return trimmed;
    }
}

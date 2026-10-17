package com.paytech.foundation.compensation.api;

/**
 * 在当前本地事务中持久化恢复审计数据的宿主适配器。
 *
 * @author Codex
 */
@FunctionalInterface
public interface CompensationRecoveryAuditRecorder {

    void record(CompensationRecoveryAudit audit);
}

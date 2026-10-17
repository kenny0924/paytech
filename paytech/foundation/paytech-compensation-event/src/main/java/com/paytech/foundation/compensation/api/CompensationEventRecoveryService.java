package com.paytech.foundation.compensation.api;

/**
 * 带审计的人工恢复边界。
 *
 * @author Codex
 */
@FunctionalInterface
public interface CompensationEventRecoveryService {

    ReopenedCompensationEvent reopen(CompensationEventReopenRequest request);
}

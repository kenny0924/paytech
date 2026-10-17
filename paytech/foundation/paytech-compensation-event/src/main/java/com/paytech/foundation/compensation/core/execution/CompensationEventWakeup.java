package com.paytech.foundation.compensation.core.execution;

/**
 * 提交已随事务落库的补偿记录，尽力立即执行。
 *
 * @author Codex
 */
@FunctionalInterface
public interface CompensationEventWakeup {

    void wakeUp(long eventId);
}

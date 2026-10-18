package com.paytech.foundation.compensation.api;

/**
 * 新任务或幂等重复任务的登记结果。
 *
 * @param eventId
 *            本地补偿记录标识
 * @param duplicate
 *            是否复用已有记录
 * @author Codex
 */
public record RegisteredCompensationEvent(long eventId, boolean duplicate) {
}

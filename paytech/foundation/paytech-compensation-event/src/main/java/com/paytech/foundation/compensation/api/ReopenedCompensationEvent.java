package com.paytech.foundation.compensation.api;

/**
 * 人工恢复成功结果。
 *
 * @param eventId
 *            已重开事件标识
 * @param lockVersion
 *            新技术版本
 * @author Codex
 */
public record ReopenedCompensationEvent(long eventId, long lockVersion) {
}

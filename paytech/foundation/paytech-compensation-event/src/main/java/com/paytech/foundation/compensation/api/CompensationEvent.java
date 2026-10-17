package com.paytech.foundation.compensation.api;

/**
 * 单次处理器执行尝试的最小输入。
 *
 * @param eventId
 *            补偿记录标识
 * @param eventKey
 *            业务事件或补偿任务的稳定标识
 * @param eventType
 *            唯一处理器类型
 * @param subEventType
 *            同一处理器下的业务子类型
 * @param payloadJson
 *            可选的最小 JSON，不作为主要业务上下文
 * @param executeCount
 *            当前执行次数
 * @author Codex
 */
public record CompensationEvent(long eventId, String eventKey, String eventType, String subEventType,
    String payloadJson, int executeCount) {
}

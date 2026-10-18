package com.paytech.foundation.compensation.api;

import tools.jackson.databind.JsonNode;

/**
 * 登记一项本服务本地补偿任务的请求。
 *
 * <p>
 * 事件身份字段是处理器的常规输入，必须共同精确标识处理器可从本服务数据库重建的某条持久化事实或操作。 {@code payload} 可选，仅用于确实没有持久化来源的少量参数。
 *
 * @param eventKey
 *            业务事件或补偿任务的稳定标识
 * @param eventType
 *            唯一处理器类型
 * @param subEventType
 *            同一处理器下的业务子类型
 * @param payload
 *            可选的最小 JSON 对象
 * @author Codex
 */
public record CompensationEventRequest(String eventKey, String eventType, String subEventType, JsonNode payload) {

    public CompensationEventRequest(String eventKey, String eventType, String subEventType) {
        this(eventKey, eventType, subEventType, null);
    }

    public CompensationEventRequest {
        eventKey = requireText("eventKey", eventKey, 128);
        eventType = requireText("eventType", eventType, 64);
        subEventType = requireText("subEventType", subEventType, 64);
        if (payload != null && !payload.isObject()) {
            throw new IllegalArgumentException("payload must be a JSON object when provided");
        }
        payload = payload == null ? null : payload.deepCopy();
    }

    private static String requireText(String field, String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maximumLength) {
            throw new IllegalArgumentException(field + " exceeds " + maximumLength + " characters");
        }
        if (trimmed.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must not contain control characters");
        }
        return trimmed;
    }

    @Override
    public JsonNode payload() {
        return payload == null ? null : payload.deepCopy();
    }
}

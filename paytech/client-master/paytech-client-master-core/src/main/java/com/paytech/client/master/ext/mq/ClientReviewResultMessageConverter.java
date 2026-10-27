package com.paytech.client.master.ext.mq;

import com.paytech.client.master.service.processor.request.ApplyClientReviewResultRequest;
import com.paytech.foundation.messaging.api.DomainEvent;

import tools.jackson.databind.JsonNode;

/**
 * compliance ClientReviewDecided 信封（compliance.events，Schema v1 与 v2）到内部请求的纯转换
 * <li>v2 追加 caseType、roundNo、tier、riskRating、dueTime、errors，outcome 增 EXPIRED；本服务只读取 dueTime，其余字段由 BFF 经结果查询展示。</li>
 * <li>信封结构与 payloadHash（RFC 8785）已由 paytech-messaging 解码器校验；这里校验事件身份与 Payload 业务字段。</li>
 * <li>aggregateId 必须等于 payload.caseId；不合法抛出 IllegalArgumentException，由 Handler 转为拒绝进入死信队列。</li>
 */
final class ClientReviewResultMessageConverter {
    /** 事件类型。 */
    static final String EVENT_TYPE = "ClientReviewDecided";

    private ClientReviewResultMessageConverter() {
    }

    /** 校验信封身份并转换。 */
    static ApplyClientReviewResultRequest convert(DomainEvent event) {
        try {
            JsonNode payload = event.payload();
            if (!EVENT_TYPE.equals(event.eventType()) || !"compliance".equals(event.sourceService())
                || !"DD_CASE".equals(event.aggregateType()) || (event.schemaVersion() != 1 && event.schemaVersion() != 2)
                || !event.aggregateId().equals(payload.path("caseId").asString())) {
                throw invalid();
            }
            return new ApplyClientReviewResultRequest(id(payload, "applicationId"), id(payload, "caseId"),
                payload.path("submissionId").asString(), payload.path("outcome").asString(), optionalId(payload, "decisionId"),
                optionalId(payload, "decidedBy"), optionalTime(payload, "dueTime"));
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    /** 必填正 BIGINT 字符串。 */
    static long id(JsonNode payload, String field) {
        String value = payload.path(field).asString();
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw invalid();
        }
        return Long.parseLong(value);
    }

    /** 可空正 BIGINT 字符串。 */
    static Long optionalId(JsonNode payload, String field) {
        return payload.path(field).isNull() || payload.path(field).isMissingNode() ? null : id(payload, field);
    }

    /** 可空 UTC ISO-8601 时间。 */
    static java.time.Instant optionalTime(JsonNode payload, String field) {
        if (payload.path(field).isNull() || payload.path(field).isMissingNode()) {
            return null;
        }
        try {
            return java.time.Instant.parse(payload.path(field).asString());
        } catch (java.time.format.DateTimeParseException malformed) {
            throw invalid();
        }
    }

    /** 不回显消息内容。 */
    static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Client review result message is invalid");
    }
}

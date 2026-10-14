package com.paytech.client.contract.ext.mq;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.paytech.client.contract.service.processor.request.ApplyClientReviewResultRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Risk ClientReviewDecided 事件信封（topic_risk_client_review / client_review.decided.v1）到内部请求的纯转换
 * <li>只接受 schemaVersion=1；payloadHash 按 Risk 合同对 payload 原字段顺序的 UTF-8 JSON 计算 SHA-256。</li>
 * <li>信封不合法抛出 IllegalArgumentException，由监听器转入死信队列。</li>
 */
final class ClientReviewResultMessageConverter {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ClientReviewResultMessageConverter() {
    }

    /** 校验信封并转换。 */
    static ApplyClientReviewResultRequest convert(byte[] body) {
        try {
            JsonNode envelope = JSON.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode payload = envelope.path("payload");
            if (!"ClientReviewDecided".equals(envelope.path("eventType").asString()) || !"risk".equals(envelope.path("sourceSystem").asString())
                || envelope.path("schemaVersion").asInt() != 1 || !payload.isObject()
                || !("sha256:" + sha256(JSON.writeValueAsBytes(payload))).equals(envelope.path("payloadHash").asString())
                || !envelope.path("sourceId").asString().equals(payload.path("caseId").asString())) {
                throw invalid();
            }
            return new ApplyClientReviewResultRequest(id(payload, "applicationId"), id(payload, "caseId"),
                payload.path("submissionId").asString(), payload.path("outcome").asString(), optionalId(payload, "decisionId"),
                optionalId(payload, "decidedBy"));
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

    /** 小写十六进制 SHA-256。 */
    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** 不回显消息内容。 */
    static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Client review result message is invalid");
    }
}

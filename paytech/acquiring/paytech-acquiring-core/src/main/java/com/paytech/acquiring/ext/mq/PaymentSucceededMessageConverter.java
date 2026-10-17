package com.paytech.acquiring.ext.mq;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import com.paytech.acquiring.service.processor.order.request.ApplyPaymentSucceededRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Payment PaymentSucceeded 事件信封（topic_payment_result / payment.succeeded.v1）到内部请求的纯转换
 * <li>只接受 schemaVersion=1；payloadHash 按 Payment 合同对 payload 原字段顺序的 UTF-8 JSON 计算 SHA-256。</li>
 * <li>ID 与金额均为字符串；sourceId 必须等于 payload.paymentAttemptId，事件键必须由尝试 ID 与来源版本组成。</li>
 * <li>信封不合法抛出 IllegalArgumentException，由监听器转入死信队列；异常消息不回显消息内容。</li>
 */
final class PaymentSucceededMessageConverter {
    /** 事件类型。 */
    static final String EVENT_TYPE = "PaymentSucceeded";

    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String AMOUNT = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PaymentSucceededMessageConverter() {
    }

    /** 校验信封并转换。 */
    static ApplyPaymentSucceededRequest convert(byte[] body) {
        try {
            JsonNode envelope = JSON.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode payload = envelope.path("payload");
            checkEnvelope(envelope, payload);
            return new ApplyPaymentSucceededRequest(text(envelope, "eventKey"),
                text(envelope, "payloadHash").substring("sha256:".length()), envelope.path("sourceVersion").asLong(),
                instant(envelope, "occurredAt"), id(payload, "clientId"), id(payload, "orderId"),
                id(payload, "paymentIntentId"), id(payload, "paymentAttemptId"), matched(payload, "entityNo", "[A-Z]{2}[0-9]{3}"),
                amount(payload, "amount"), matched(payload, "currency", "[A-Z]{3}"), amount(payload, "capturedAmount"),
                instant(payload, "paymentSucceededTime"));
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    /** 信封身份、版本、摘要与来源一致性。 */
    static void checkEnvelope(JsonNode envelope, JsonNode payload) {
        if (!EVENT_TYPE.equals(text(envelope, "eventType")) || !"payment".equals(text(envelope, "sourceSystem"))
            || !"PAYMENT_ATTEMPT".equals(text(envelope, "sourceType")) || !envelope.path("schemaVersion").isIntegralNumber()
            || envelope.path("schemaVersion").asInt() != 1 || !payload.isObject()
            || !"COMMERCE_ORDER".equals(text(payload, "sourceType"))) {
            throw invalid();
        }
        if (!("sha256:" + sha256(JSON.writeValueAsBytes(payload))).equals(text(envelope, "payloadHash"))) {
            throw invalid();
        }
        String attemptId = matched(payload, "paymentAttemptId", ID);
        JsonNode version = envelope.path("sourceVersion");
        if (!attemptId.equals(text(envelope, "sourceId")) || !version.isIntegralNumber() || version.asLong() <= 0
            || !text(envelope, "eventKey").equals(
                "PAYMENT:PAYMENT_ATTEMPT:" + attemptId + ":" + version.asLong() + ":" + EVENT_TYPE)) {
            throw invalid();
        }
    }

    /** 必填字符串字段。 */
    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isString()) {
            throw invalid();
        }
        return value.asString();
    }

    /** 必填且符合格式的字符串字段。 */
    static String matched(JsonNode node, String field, String pattern) {
        String value = text(node, field);
        if (!value.matches(pattern)) {
            throw invalid();
        }
        return value;
    }

    /** 必填正 BIGINT 字符串。 */
    static long id(JsonNode node, String field) {
        return Long.parseLong(matched(node, field, ID));
    }

    /** 必填非负十进制金额字符串，不接受科学计数法。 */
    static BigDecimal amount(JsonNode node, String field) {
        return new BigDecimal(matched(node, field, AMOUNT));
    }

    /** 必填 RFC 3339 UTC 时间。 */
    static Instant instant(JsonNode node, String field) {
        return Instant.parse(text(node, field));
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
        return new IllegalArgumentException("Payment succeeded message is invalid");
    }
}

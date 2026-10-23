package com.paytech.acquiring.ext.mq;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import com.paytech.acquiring.domain.order.OrderPaymentFacts;
import com.paytech.acquiring.service.processor.order.request.ApplyPaymentSucceededRequest;
import com.paytech.foundation.messaging.api.DomainEvent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Payment PaymentSucceeded 信封（payment.events，Schema v1）到内部请求的纯转换
 * <li>信封结构与 payloadHash（RFC 8785）已由 paytech-messaging 解码器校验；这里校验事件身份与 Payload 业务字段。</li>
 * <li>业务幂等键沿用旧报文口径，与切换前已落地的投影一致：事件键 {@code PAYMENT:PAYMENT_ATTEMPT:{attemptId}:{version}:PaymentSucceeded}，
 * 载荷 Hash 为 payload 按原字段顺序紧凑序列化后 UTF-8 字节的 SHA-256。</li>
 * <li>ID 与金额均为字符串；aggregateId 必须等于 payload.paymentAttemptId。</li>
 * <li>另读可空的渠道、渠道交易引用与卡属性字段，随支付事实转发给 OrderPaymentSucceeded；缺失的老报文仍可落地。</li>
 * <li>不合法抛出 IllegalArgumentException，由 Handler 转为拒绝进入死信队列；异常消息不回显消息内容。</li>
 */
final class PaymentSucceededMessageConverter {
    /** 事件类型。 */
    static final String EVENT_TYPE = "PaymentSucceeded";

    private static final String AGGREGATE_TYPE = "PAYMENT_ATTEMPT";
    /** 接受的信封版本：v1 为切换前报文；v2（第 12 步）只追加 card 使用的 instrumentRef / consentToSave / customerId，本服务不读取。 */
    private static final int MIN_SCHEMA_VERSION = 1;
    private static final int MAX_SCHEMA_VERSION = 2;
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String AMOUNT = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PaymentSucceededMessageConverter() {
    }

    /** 校验信封身份并转换。 */
    static ApplyPaymentSucceededRequest convert(DomainEvent event) {
        try {
            JsonNode payload = event.payload();
            checkEnvelope(event, payload);
            return new ApplyPaymentSucceededRequest(legacyEventKey(event), sha256(JSON.writeValueAsBytes(payload)),
                event.aggregateVersion(), event.occurredAt(), id(payload, "clientId"), id(payload, "orderId"),
                id(payload, "paymentIntentId"), matched(payload, "entityNo", "[A-Z]{2}[0-9]{3}"),
                amount(payload, "amount"), matched(payload, "currency", "[A-Z]{3}"), paymentFacts(payload));
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    /**
     * 支付事实：成功尝试、已捕获金额与成功时间必填；渠道与卡属性可空
     * <li>渠道交易引用 ID 与类型为 2026-10-22 末尾追加字段，老报文没有；卡属性非卡支付为 null。</li>
     * <li>出现但类型或格式不符仍按非法报文拒绝。</li>
     */
    static OrderPaymentFacts paymentFacts(JsonNode payload) {
        return new OrderPaymentFacts(id(payload, "paymentAttemptId"), amount(payload, "capturedAmount"),
            instant(payload, "paymentSucceededTime"), optionalText(payload, "paymentMethodCode"),
            optionalId(payload, "channelId"), optionalId(payload, "channelAccountId"), optionalId(payload, "midId"),
            optionalText(payload, "channelTransactionRef"), optionalId(payload, "channelTransactionRefId"),
            optionalText(payload, "channelReferenceType"), optionalText(payload, "cardBrand"),
            optionalText(payload, "fundingType"), optionalText(payload, "cardLevel"));
    }

    /** 事件身份、版本与来源一致性。 */
    static void checkEnvelope(DomainEvent event, JsonNode payload) {
        if (!EVENT_TYPE.equals(event.eventType()) || !"payment".equals(event.sourceService())
            || !AGGREGATE_TYPE.equals(event.aggregateType())
            || event.schemaVersion() < MIN_SCHEMA_VERSION || event.schemaVersion() > MAX_SCHEMA_VERSION
            || !"COMMERCE_ORDER".equals(text(payload, "sourceType"))) {
            throw invalid();
        }
        String attemptId = matched(payload, "paymentAttemptId", ID);
        if (!attemptId.equals(event.aggregateId()) || event.aggregateVersion() <= 0) {
            throw invalid();
        }
    }

    /** 切换前旧报文的事件键，Commerce 投影以它防重。 */
    static String legacyEventKey(DomainEvent event) {
        return "PAYMENT:" + AGGREGATE_TYPE + ":" + event.aggregateId() + ":" + event.aggregateVersion() + ":" + EVENT_TYPE;
    }

    /** 必填字符串字段。 */
    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isString()) {
            throw invalid();
        }
        return value.asString();
    }

    /** 可空字符串字段：缺失或 JSON null 为空，其他非字符串类型拒绝。 */
    static String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw invalid();
        }
        return value.asString();
    }

    /** 可空正 BIGINT 字符串字段，原样保留十进制文本。 */
    static String optionalId(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value != null && !value.matches(ID)) {
            throw invalid();
        }
        return value;
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

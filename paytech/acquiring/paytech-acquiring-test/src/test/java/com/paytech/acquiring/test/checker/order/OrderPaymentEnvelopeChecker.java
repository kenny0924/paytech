package com.paytech.acquiring.test.checker.order;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.paytech.acquiring.domain.order.OrderPaymentSucceededPayload;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.DomainEvent;

import tools.jackson.databind.JsonNode;

/** 已收到的 OrderPaymentSucceeded 信封；期望值来自用例插入的前置事实与发布的支付事实。 */
public class OrderPaymentEnvelopeChecker {
    /** Payload 字段顺序（technique 4.3，订单组在前、支付组在后）。 */
    public static final List<String> PAYLOAD_FIELDS = List.of("orderId", "orderContextId", "clientId", "entityNo",
        "entitySourceType", "localPartnerId", "industryCode", "mccCode", "orderAmount", "currency",
        "commercialContextTime", "clientAgreementId", "clientPricingVersionId", "partnerAgreementId",
        "partnerAgreementOperatingEntityId", "partnerShareVersionId", "platformChargeVersionId", "platformServicePartyId",
        "financeTaxSnapshotId", "consumerCountryCode", "paymentIntentId", "paymentAttemptId", "capturedAmount",
        "paymentSucceededTime", "paymentMethodCode", "channelId", "channelAccountId", "midId", "channelTransactionRef",
        "channelTransactionRefId", "channelReferenceType", "cardBrand", "fundingType", "cardLevel");

    private final DomainEvent decoded;
    private final JsonNode envelope;

    /** 消费端解码器已重算 payloadHash；原始 JSON 用于检查字段顺序与 null。 */
    public OrderPaymentEnvelopeChecker(DomainEvent decoded, JsonNode envelope) {
        this.decoded = decoded;
        this.envelope = envelope;
    }

    /** 信封：聚合为订单与推进 PAID 后的版本，eventId 为四段式事件键，occurredAt 为支付成功时间。 */
    public OrderPaymentEnvelopeChecker envelope(long orderId, long orderVersion, Instant paymentSucceededTime) {
        String eventId = AggregateKey.of(OrderPaymentSucceededPayload.AGGREGATE_TYPE, Long.toString(orderId))
            .eventKey(OrderPaymentSucceededPayload.EVENT_TYPE, orderVersion);
        assertAll(() -> assertEquals(eventId, decoded.eventId()),
            () -> assertEquals(OrderPaymentSucceededPayload.EVENT_TYPE, decoded.eventType()),
            () -> assertEquals(OrderPaymentSucceededPayload.SCHEMA_VERSION, decoded.schemaVersion()),
            () -> assertEquals(OrderPaymentSucceededPayload.AGGREGATE_TYPE, decoded.aggregateType()),
            () -> assertEquals(Long.toString(orderId), decoded.aggregateId()),
            () -> assertEquals(orderVersion, decoded.aggregateVersion()),
            () -> assertEquals("acquiring", decoded.sourceService()),
            () -> assertEquals("main", decoded.instanceId()),
            () -> assertEquals(paymentSucceededTime, decoded.occurredAt()),
            () -> assertTrue(decoded.payloadHash().matches("sha256:[0-9a-f]{64}")));
        return this;
    }

    /** Payload 字段名与顺序精确等于合同，并逐字段比较全部字段值。 */
    public OrderPaymentEnvelopeChecker payload(Map<String, String> expected) {
        assertEquals(PAYLOAD_FIELDS, List.copyOf(expected.keySet()), "期望值应覆盖全部字段");
        return fields(expected);
    }

    /** Payload 字段名与顺序精确等于合同，逐个比较给定字段的字符串值；期望为 null 时必须是 JSON null。 */
    public OrderPaymentEnvelopeChecker fields(Map<String, String> expected) {
        JsonNode payload = envelope.path("payload");
        assertEquals(PAYLOAD_FIELDS, new ArrayList<>(payload.propertyNames()), "Payload 字段顺序");
        expected.forEach((field, want) -> {
            JsonNode value = payload.path(field);
            if (want == null) {
                assertTrue(value.isNull(), field + " 应为 JSON null，实际：" + value);
            } else {
                assertTrue(value.isString(), field + " 应为字符串");
                assertEquals(want, value.asString(), field);
            }
        });
        return this;
    }
}

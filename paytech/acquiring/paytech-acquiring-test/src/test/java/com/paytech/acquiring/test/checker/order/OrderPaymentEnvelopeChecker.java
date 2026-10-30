package com.paytech.acquiring.test.checker.order;

import java.math.BigDecimal;

import com.paytech.tax.api.enums.TaxCalculationStatus;
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
    /** Payload 字段顺序（technique 4.3，订单组在前、支付组在后；tax 需求 11.1 可空新增的税务组最后）。 */
    public static final List<String> PAYLOAD_FIELDS = List.of("orderId", "orderContextId", "clientId", "entityNo",
        "entitySourceType", "localPartnerId", "industryCode", "mccCode", "orderAmount", "currency",
        "commercialContextTime", "clientAgreementId", "clientPricingVersionId", "partnerAgreementId",
        "partnerAgreementOperatingEntityId", "partnerShareVersionId", "platformChargeVersionId", "platformServicePartyId",
        "financeTaxSnapshotId", "consumerCountryCode", "paymentIntentId", "paymentAttemptId", "capturedAmount",
        "paymentSucceededTime", "paymentMethodCode", "channelId", "channelAccountId", "midId", "channelTransactionRef",
        "channelTransactionRefId", "channelReferenceType", "cardBrand", "fundingType", "cardLevel", "taxCalculationId",
        "taxStatus", "taxScopeKey", "taxAmount", "taxComponents");

    /** 税务组件元素的字段顺序。 */
    public static final List<String> TAX_COMPONENT_FIELDS = List.of("jurisdictionCode", "taxType", "registrationId",
        "taxSubjectPartyId", "taxAmount");

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

    /**
     * 税务字段满足 billing 消费方的死信规则（billing 按这些规则判定载荷一致，不一致进死信）
     * <li>任一税务字段非空则 taxCalculationId 非空；不发布 BLOCKED；NOT_MANAGED 没有组件；READY 时 taxAmount 等于组件合计、每个组件有
     * registrationId、所有组件同一纳税主体。</li>
     */
    public OrderPaymentEnvelopeChecker assertBillingTaxShape() {
        JsonNode payload = envelope.path("payload");
        boolean anyTax = List.of("taxStatus", "taxScopeKey", "taxAmount", "taxComponents").stream()
            .anyMatch(field -> !payload.path(field).isNull());
        if (!anyTax && payload.path("taxCalculationId").isNull()) {
            return this;
        }
        assertTrue(payload.path("taxCalculationId").isString(), "有税务字段时必须有 taxCalculationId");
        String status = payload.path("taxStatus").asString();
        assertTrue(!TaxCalculationStatus.BLOCKED.getCode().equals(status), "不发布 BLOCKED");
        JsonNode components = payload.path("taxComponents");
        if (TaxCalculationStatus.NOT_MANAGED.getCode().equals(status)) {
            assertEquals(0, components.size(), "NOT_MANAGED 没有组件");
            return this;
        }
        assertEquals(TaxCalculationStatus.READY.getCode(), status, "税务状态");
        BigDecimal sum = BigDecimal.ZERO;
        var subjects = new java.util.HashSet<String>();
        for (JsonNode component : components) {
            assertTrue(component.path("registrationId").isString(), "组件必须有 registrationId");
            subjects.add(component.path("taxSubjectPartyId").asString());
            sum = sum.add(new BigDecimal(component.path("taxAmount").asString()));
        }
        assertEquals(0, sum.compareTo(new BigDecimal(payload.path("taxAmount").asString())), "taxAmount 等于组件合计");
        assertTrue(subjects.size() <= 1, "所有组件同一纳税主体");
        return this;
    }

    /**
     * taxComponents 为 JSON 数组，元素字段与顺序固定，逐元素比较字符串值
     *
     * @param expected 期望组件，按数组顺序；空列表表示空数组（NOT_MANAGED）
     * @return 当前 Checker
     */
    public OrderPaymentEnvelopeChecker taxComponents(List<Map<String, String>> expected) {
        JsonNode components = envelope.path("payload").path("taxComponents");
        assertTrue(components.isArray(), "taxComponents 应为数组，实际：" + components);
        assertEquals(expected.size(), components.size(), "taxComponents 数量");
        for (int index = 0; index < expected.size(); index++) {
            JsonNode component = components.get(index);
            assertEquals(TAX_COMPONENT_FIELDS, new ArrayList<>(component.propertyNames()), "税务组件字段顺序");
            expected.get(index).forEach((field, want) -> assertEquals(want, component.path(field).asString(), field));
        }
        return this;
    }
}

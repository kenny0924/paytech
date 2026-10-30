package com.paytech.tax.test.builder;

import java.util.UUID;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.tax.api.enums.TaxCalculationStatus;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * acquiring OrderPaymentSucceeded Schema v1 信封（technique 11.1：订单组在前、支付组在后、税务组最后；字符串标量，缺省 JSON null）
 * <li>template() 为存量订单载荷（税务字段全部 null）；taxCalculation(...) 写入新订单税务组；build() 不修复负例。</li>
 */
public final class OrderPaymentSucceededEnvelopeBuilder {
    private final ObjectNode payload = JsonNodeFactory.instance.objectNode();

    private long orderId = TaxTestConstants.ORDER_ID;

    private long aggregateVersion = TaxTestConstants.ORDER_VERSION;

    private String eventId;

    /** 只能从模板开始。 */
    private OrderPaymentSucceededEnvelopeBuilder() {
    }

    /** 存量订单：100.00 USD、税务字段 null。 */
    public static OrderPaymentSucceededEnvelopeBuilder template() {
        OrderPaymentSucceededEnvelopeBuilder builder = new OrderPaymentSucceededEnvelopeBuilder();
        builder.order(TaxTestConstants.ORDER_ID);
        ObjectNode payload = builder.payload;
        payload.put("clientId", Long.toString(TaxTestConstants.BUYER_CLIENT_ID));
        payload.put("entityNo", TaxTestConstants.ENTITY_NO);
        payload.put("orderAmount", "100.00");
        payload.put("currency", TaxTestConstants.USD);
        payload.putNull("financeTaxSnapshotId");
        payload.put("consumerCountryCode", "SA");
        payload.put("paymentIntentId", "550000000001");
        payload.put("paymentAttemptId", "550000000002");
        payload.put("capturedAmount", "100.00");
        payload.put("paymentSucceededTime", TaxTestConstants.PAYMENT_SUCCEEDED_TIME.toString());
        for (String field : new String[] {"taxCalculationId", "taxStatus", "taxScopeKey", "taxAmount", "taxComponents"}) {
            payload.putNull(field);
        }
        return builder;
    }

    /** 订单 ID（同时是聚合 ID）。 */
    public OrderPaymentSucceededEnvelopeBuilder order(long orderId) {
        this.orderId = orderId;
        payload.put("orderId", Long.toString(orderId));
        return this;
    }

    /** 固定事件 ID（重复投递用例）；缺省按订单与版本生成新的随机 ID。 */
    public OrderPaymentSucceededEnvelopeBuilder eventId(String eventId) {
        this.eventId = eventId;
        return this;
    }

    /** 聚合版本。 */
    public OrderPaymentSucceededEnvelopeBuilder aggregateVersion(long aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
        return this;
    }

    /** 订单金额与收取金额。 */
    public OrderPaymentSucceededEnvelopeBuilder amount(String amount) {
        payload.put("orderAmount", amount);
        payload.put("capturedAmount", amount);
        return this;
    }

    /**
     * 新订单税务组
     *
     * @param calculationId 计算 ID
     * @param status 计算状态
     * @param taxAmount 税额合计；可空
     * @param components 组件（{@link #component}）；为空写空数组
     * @return 本构造器
     */
    public OrderPaymentSucceededEnvelopeBuilder taxCalculation(String calculationId, TaxCalculationStatus status,
        String taxAmount, ObjectNode... components) {
        payload.put("taxCalculationId", calculationId);
        payload.put("taxStatus", status.getCode());
        payload.put("taxScopeKey", "SALE");
        payload.put("taxAmount", taxAmount);
        ArrayNode array = payload.putArray("taxComponents");
        for (ObjectNode component : components) {
            array.add(component);
        }
        return this;
    }

    /** 覆盖任意字段（负例）。 */
    public OrderPaymentSucceededEnvelopeBuilder set(String field, String value) {
        payload.put(field, value);
        return this;
    }

    /**
     * 一个汇总税额组件
     *
     * @param jurisdiction 辖区
     * @param registrationId 注册 ID
     * @param taxAmount 税额
     * @return 组件 JSON
     */
    public static ObjectNode component(String jurisdiction, String registrationId, String taxAmount) {
        ObjectNode component = JsonNodeFactory.instance.objectNode();
        component.put("jurisdictionCode", jurisdiction);
        component.put("registrationId", registrationId);
        component.put("taxSubjectPartyId", Long.toString(TaxTestConstants.SUBJECT_PARTY_ID));
        component.put("taxAmount", taxAmount);
        return component;
    }

    /** 构造 acquiring 身份的信封。 */
    public DomainEvent build() {
        DomainEvent.Builder builder = DomainEvent.builder().eventType("OrderPaymentSucceeded").schemaVersion(1)
            .aggregate("ORDER", Long.toString(orderId)).aggregateVersion(aggregateVersion)
            .occurredAt(TaxTestConstants.PAYMENT_SUCCEEDED_TIME).sourceService("acquiring").instanceId("main")
            .payload(payload.deepCopy());
        if (eventId != null) {
            builder.eventId(eventId);
        } else {
            builder.eventId("ORDER:" + orderId + ":OrderPaymentSucceeded:" + aggregateVersion + ":" + UUID.randomUUID());
        }
        return builder.build();
    }
}

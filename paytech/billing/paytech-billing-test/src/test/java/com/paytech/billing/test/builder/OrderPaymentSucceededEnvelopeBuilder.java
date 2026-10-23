package com.paytech.billing.test.builder;

import static com.paytech.billing.test.builder.PaymentFactConstants.CARD_BRAND;
import static com.paytech.billing.test.builder.PaymentFactConstants.CARD_LEVEL;
import static com.paytech.billing.test.builder.PaymentFactConstants.CHANNEL_ACCOUNT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CHANNEL_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CHANNEL_REFERENCE_TYPE;
import static com.paytech.billing.test.builder.PaymentFactConstants.CHANNEL_TRANSACTION_REF;
import static com.paytech.billing.test.builder.PaymentFactConstants.CHANNEL_TRANSACTION_REF_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_AGREEMENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_PRICING_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.COMMERCIAL_CONTEXT_TIME;
import static com.paytech.billing.test.builder.PaymentFactConstants.CONSUMER_COUNTRY;
import static com.paytech.billing.test.builder.PaymentFactConstants.ENTITY_NO;
import static com.paytech.billing.test.builder.PaymentFactConstants.FUNDING_TYPE;
import static com.paytech.billing.test.builder.PaymentFactConstants.INDUSTRY_CODE;
import static com.paytech.billing.test.builder.PaymentFactConstants.LOCAL_PARTNER_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.MCC_CODE;
import static com.paytech.billing.test.builder.PaymentFactConstants.MID_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.ORDER_CONTEXT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.ORDER_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.ORDER_VERSION;
import static com.paytech.billing.test.builder.PaymentFactConstants.PARTNER_AGREEMENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PARTNER_AGREEMENT_OE_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PARTNER_SHARE_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PAYMENT_ATTEMPT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PAYMENT_INTENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PAYMENT_METHOD;
import static com.paytech.billing.test.builder.PaymentFactConstants.PAYMENT_SUCCEEDED_TIME;
import static com.paytech.billing.test.builder.PaymentFactConstants.PLATFORM_CHARGE_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PLATFORM_SERVICE_PARTY_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.TAX_SNAPSHOT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.USD;

import java.time.Instant;

import com.paytech.foundation.messaging.api.DomainEvent;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * acquiring OrderPaymentSucceeded Schema v1 信封（技术方案 4.3 字段表：订单组在前、支付组在后，字符串标量，缺省 JSON null）
 * <li>template() 为 PLATFORM 来源、100.00 USD 的卡支付，Partner 五项为 null、税务快照有值；partner() 补齐 Partner 五项；build() 不修复负例。</li>
 */
public final class OrderPaymentSucceededEnvelopeBuilder {
    /** 发布方服务名。 */
    public static final String ACQUIRING_SERVICE = "acquiring";

    /** 事件类型。 */
    public static final String EVENT_TYPE = "OrderPaymentSucceeded";

    /** 聚合类型。 */
    public static final String AGGREGATE_TYPE = "ORDER";

    /** Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    private final ObjectNode payload = JsonNodeFactory.instance.objectNode();

    private String sourceService = ACQUIRING_SERVICE;

    private int schemaVersion = SCHEMA_VERSION;

    private long aggregateVersion = ORDER_VERSION;

    /** 只能从模板开始。 */
    private OrderPaymentSucceededEnvelopeBuilder() {
    }

    /** 默认订单支付成功：PLATFORM 来源、无 Partner、100.00 USD、VISA 信用卡、渠道交易引用三项齐全。 */
    public static OrderPaymentSucceededEnvelopeBuilder template() {
        OrderPaymentSucceededEnvelopeBuilder builder = new OrderPaymentSucceededEnvelopeBuilder();
        builder.putOrder();
        builder.putPayment();
        return builder;
    }

    /** 订单组字段。 */
    void putOrder() {
        payload.put("orderId", Long.toString(ORDER_ID));
        payload.put("orderContextId", Long.toString(ORDER_CONTEXT_ID));
        payload.put("clientId", Long.toString(CLIENT_ID));
        payload.put("entityNo", ENTITY_NO);
        payload.put("entitySourceType", "PLATFORM");
        payload.putNull("localPartnerId");
        payload.put("industryCode", INDUSTRY_CODE);
        payload.put("mccCode", MCC_CODE);
        payload.put("orderAmount", "100.00");
        payload.put("currency", USD);
        payload.put("commercialContextTime", COMMERCIAL_CONTEXT_TIME.toString());
        payload.put("clientAgreementId", Long.toString(CLIENT_AGREEMENT_ID));
        payload.put("clientPricingVersionId", Long.toString(CLIENT_PRICING_VERSION_ID));
        for (String field : new String[] {"partnerAgreementId", "partnerAgreementOperatingEntityId",
            "partnerShareVersionId", "platformChargeVersionId", "platformServicePartyId"}) {
            payload.putNull(field);
        }
        payload.put("financeTaxSnapshotId", Long.toString(TAX_SNAPSHOT_ID));
        payload.put("consumerCountryCode", CONSUMER_COUNTRY);
    }

    /** 支付组字段。 */
    void putPayment() {
        payload.put("paymentIntentId", Long.toString(PAYMENT_INTENT_ID));
        payload.put("paymentAttemptId", Long.toString(PAYMENT_ATTEMPT_ID));
        payload.put("capturedAmount", "100.00");
        payload.put("paymentSucceededTime", PAYMENT_SUCCEEDED_TIME.toString());
        payload.put("paymentMethodCode", PAYMENT_METHOD);
        payload.put("channelId", Long.toString(CHANNEL_ID));
        payload.put("channelAccountId", Long.toString(CHANNEL_ACCOUNT_ID));
        payload.put("midId", Long.toString(MID_ID));
        payload.put("channelTransactionRef", CHANNEL_TRANSACTION_REF);
        payload.put("channelTransactionRefId", Long.toString(CHANNEL_TRANSACTION_REF_ID));
        payload.put("channelReferenceType", CHANNEL_REFERENCE_TYPE);
        payload.put("cardBrand", CARD_BRAND);
        payload.put("fundingType", FUNDING_TYPE);
        payload.put("cardLevel", CARD_LEVEL);
    }

    /** PARTNER 来源：Local Partner、Partner 合同、OE 关联、两条款版本与平台签约 Party。 */
    public OrderPaymentSucceededEnvelopeBuilder partner() {
        payload.put("entitySourceType", "PARTNER");
        payload.put("localPartnerId", Long.toString(LOCAL_PARTNER_ID));
        payload.put("partnerAgreementId", Long.toString(PARTNER_AGREEMENT_ID));
        payload.put("partnerAgreementOperatingEntityId", Long.toString(PARTNER_AGREEMENT_OE_ID));
        payload.put("partnerShareVersionId", Long.toString(PARTNER_SHARE_VERSION_ID));
        payload.put("platformChargeVersionId", Long.toString(PLATFORM_CHARGE_VERSION_ID));
        payload.put("platformServicePartyId", Long.toString(PLATFORM_SERVICE_PARTY_ID));
        return this;
    }

    /** 订单金额与收取金额。 */
    public OrderPaymentSucceededEnvelopeBuilder amount(String amount) {
        payload.put("orderAmount", amount);
        payload.put("capturedAmount", amount);
        return this;
    }

    /** 支付成功时间。 */
    public OrderPaymentSucceededEnvelopeBuilder succeededAt(Instant succeededAt) {
        payload.put("paymentSucceededTime", succeededAt.toString());
        return this;
    }

    /** 覆盖一个字符串字段；value 为 null 时写 JSON null。 */
    public OrderPaymentSucceededEnvelopeBuilder set(String field, String value) {
        payload.put(field, value);
        return this;
    }

    /** 删除一个字段（缺字段负例）。 */
    public OrderPaymentSucceededEnvelopeBuilder remove(String field) {
        payload.remove(field);
        return this;
    }

    /** 信封发布方服务名（负例）。 */
    public OrderPaymentSucceededEnvelopeBuilder sourceService(String sourceService) {
        this.sourceService = sourceService;
        return this;
    }

    /** 信封 Schema 版本（负例）。 */
    public OrderPaymentSucceededEnvelopeBuilder schemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
        return this;
    }

    /** 信封聚合版本。 */
    public OrderPaymentSucceededEnvelopeBuilder aggregateVersion(long aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
        return this;
    }

    /** 构建信封：聚合 ID 取 Payload 的 orderId，发生时间为支付成功时间。 */
    public DomainEvent build() {
        return DomainEvent.builder().eventType(EVENT_TYPE).schemaVersion(schemaVersion)
            .aggregate(AGGREGATE_TYPE, payload.path("orderId").asString()).aggregateVersion(aggregateVersion)
            .occurredAt(PAYMENT_SUCCEEDED_TIME).sourceService(sourceService).instanceId("main").payload(payload.deepCopy())
            .build();
    }
}

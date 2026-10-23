package com.paytech.acquiring.domain.order;

import java.math.BigDecimal;

import com.paytech.foundation.money.Money;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * OrderPaymentSucceeded Payload：acquiring.events 信封 Schema v1（需求 billing-支付事实自动登记 technique 4.3）
 * <li>固定字段顺序：订单与冻结商业字段在前，支付事实在后。</li>
 * <li>ID 与金额一律十进制字符串（金额按订单币种精度输出主单位），时间为 UTC RFC 3339，缺失为 JSON null，符合 Foundation JSON
 * Object Profile。</li>
 * <li>不含消费者资料、法律披露、公开卖家信息、风控引用与税务摘要。</li>
 */
public final class OrderPaymentSucceededPayload {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "OrderPaymentSucceeded";

    /** acquiring.events 信封的 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 聚合类型：订单。 */
    public static final String AGGREGATE_TYPE = "ORDER";

    /** 禁止实例化。 */
    private OrderPaymentSucceededPayload() {
    }

    /**
     * 按固定顺序组装 Payload
     *
     * @param source 已判定可发布的订单冻结商业字段与支付投影
     * @param facts 登记时冻结的支付事实
     * @return JSON Object
     */
    public static ObjectNode json(OrderPaymentEventSource source, OrderPaymentFacts facts) {
        var context = source.getContext();
        var payment = source.getPayment();
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("orderId", Long.toString(context.getOrderId()));
        payload.put("orderContextId", text(context.getOrderContextId()));
        payload.put("clientId", Long.toString(context.getClientId()));
        payload.put("entityNo", context.getEntityNo());
        payload.put("entitySourceType", context.getEntitySourceType());
        payload.put("localPartnerId", text(context.getLocalPartnerId()));
        payload.put("industryCode", context.getIndustryCode());
        payload.put("mccCode", context.getMccCode());
        payload.put("orderAmount", amount(context.getCurrency(), context.getTotalAmount()));
        payload.put("currency", context.getCurrency());
        payload.put("commercialContextTime", context.getCommercialContextTime().toString());
        payload.put("clientAgreementId", text(context.getClientAgreementId()));
        payload.put("clientPricingVersionId", text(context.getClientPricingVersionId()));
        payload.put("partnerAgreementId", text(context.getPartnerAgreementId()));
        payload.put("partnerAgreementOperatingEntityId", text(context.getPartnerAgreementOperatingEntityId()));
        payload.put("partnerShareVersionId", text(context.getPartnerShareVersionId()));
        payload.put("platformChargeVersionId", text(context.getPlatformChargeVersionId()));
        payload.put("platformServicePartyId", text(context.getPlatformServicePartyId()));
        payload.put("financeTaxSnapshotId", text(context.getFinanceTaxSnapshotId()));
        payload.put("consumerCountryCode", context.getConsumerCountryCode());
        payload.put("paymentIntentId", Long.toString(payment.getPaymentIntentId()));
        payload.put("paymentAttemptId", Long.toString(facts.getPaymentAttemptId()));
        payload.put("capturedAmount", amount(context.getCurrency(), facts.getCapturedAmount()));
        payload.put("paymentSucceededTime", facts.getPaymentSucceededTime().toString());
        payload.put("paymentMethodCode", facts.getPaymentMethodCode());
        payload.put("channelId", facts.getChannelId());
        payload.put("channelAccountId", facts.getChannelAccountId());
        payload.put("midId", facts.getMidId());
        payload.put("channelTransactionRef", facts.getChannelTransactionRef());
        payload.put("channelTransactionRefId", facts.getChannelTransactionRefId());
        payload.put("channelReferenceType", facts.getChannelReferenceType());
        payload.put("cardBrand", facts.getCardBrand());
        payload.put("fundingType", facts.getFundingType());
        payload.put("cardLevel", facts.getCardLevel());
        return payload;
    }

    /** 可空 ID 转十进制字符串。 */
    private static String text(Long id) {
        return id == null ? null : id.toString();
    }

    /** 按订单币种精度输出主单位金额字符串。 */
    private static String amount(String currency, BigDecimal value) {
        return Money.of(currency, value).toAmountString();
    }
}

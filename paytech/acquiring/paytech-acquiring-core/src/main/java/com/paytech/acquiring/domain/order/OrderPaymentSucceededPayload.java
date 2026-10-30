package com.paytech.acquiring.domain.order;

import java.math.BigDecimal;

import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;
import com.paytech.acquiring.domain.customer.OrderTaxQuotes;
import com.paytech.foundation.money.Money;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * OrderPaymentSucceeded Payload：acquiring.events 信封 Schema v1（需求 billing-支付事实自动登记 technique 4.3）
 * <li>固定字段顺序：订单与冻结商业字段在前，支付事实在后，税务字段最后。</li>
 * <li>ID 与金额一律十进制字符串（金额按订单币种精度输出主单位），时间为 UTC RFC 3339，缺失为 JSON null，符合 Foundation JSON
 * Object Profile。</li>
 * <li>2026-10-29 tax 结账算税需求（technique 11.1、S-08）可空新增税务字段，schemaVersion 保持 1：taxCalculationId（支付所依据的
 * tax 计算）、taxStatus（READY / NOT_MANAGED）、taxScopeKey（SALE）、taxAmount、taxComponents[]{jurisdictionCode, taxType,
 * registrationId, taxSubjectPartyId, taxAmount}（按辖区与注册汇总，取自 t_order_tax_calculation.components_json，不回查 tax；
 * NOT_MANAGED 为空数组）。READY 时 taxAmount 等于组件合计且组件只有一个纳税主体（报价落库前已校验，与 billing 死信规则一致）。
 * 税务服务上线前的历史订单五个字段均为 null；新订单 financeTaxSnapshotId 为 null（该字段保留待后续删除）。</li>
 * <li>不含消费者资料、法律披露、公开卖家信息与风控引用。</li>
 */
public final class OrderPaymentSucceededPayload {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "OrderPaymentSucceeded";

    /** acquiring.events 信封的 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 聚合类型：订单。 */
    public static final String AGGREGATE_TYPE = "ORDER";

    /** 税务范围：acquiring 本期只发起销售计算。 */
    public static final String TAX_SCOPE_KEY = "SALE";

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
        putTax(payload, context.getCurrency(), source.getTax());
        return payload;
    }

    /** 追加可空税务字段；历史订单没有税务计算时全部为 null。 */
    private static void putTax(ObjectNode payload, String currency, OrderTaxCalculation tax) {
        if (tax == null) {
            payload.putNull("taxCalculationId");
            payload.putNull("taxStatus");
            payload.putNull("taxScopeKey");
            payload.putNull("taxAmount");
            payload.putNull("taxComponents");
            return;
        }
        payload.put("taxCalculationId", Long.toString(tax.getTaxCalculationId()));
        payload.put("taxStatus", tax.getTaxStatus().getCode());
        payload.put("taxScopeKey", TAX_SCOPE_KEY);
        payload.put("taxAmount", tax.getTaxAmount() == null ? null : amount(currency, tax.getTaxAmount()));
        payload.set("taxComponents", OrderTaxQuotes.components(tax.getComponentsJson()));
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

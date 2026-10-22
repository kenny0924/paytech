package com.paytech.payment.execution.domain;

import java.util.LinkedHashMap;
import java.util.Map;

import com.paytech.payment.execution.core.action.PayActionActor;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * PaymentSucceeded Payload：payment.events 信封 Schema v2（design/08 §3、D34，design/05 §2.3）。
 * <li>固定字段顺序；ID 与金额一律十进制字符串，时间为 UTC RFC 3339，布尔为 JSON Boolean，缺失为 JSON null，符合 Foundation JSON Object Profile。</li>
 * <li>只含最小集合，不含卡数据、付款人引用、IP 与 User-Agent。v2 在 v1 之后追加 instrumentRef（Card 工具引用，不是卡数据或可用凭据）、
 * consentToSave、customerId，供 card 标记凭证已验证并存卡；acquiring 忽略新增字段。</li>
 * <li>BIN 库需求（2026-10-22）在 v2 末尾再追加可空 cardBrand、fundingType、cardLevel（Token 冻结的卡属性快照，未识别或非卡支付为
 * null），只追加可空字段，Schema 版本仍为 2；供 billing 写入 card_network、funding_type、card_level。</li>
 */
public final class PaymentSucceededPayload {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "PaymentSucceeded";

    /** payment.events 信封的 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 2;

    /** 聚合类型：支付尝试。 */
    public static final String AGGREGATE_TYPE = "PAYMENT_ATTEMPT";

    private PaymentSucceededPayload() {
    }

    /** v1 固定字段顺序的最小字段集合（v2 Payload 的前缀）；金额按币种精度写成主单位十进制字符串。 */
    public static Map<String, String> fields(PaymentSucceededResult result) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("clientId", Long.toString(result.getClientId()));
        payload.put("orderId", Long.toString(result.getSourceId()));
        payload.put("sourceType", result.getSourceType());
        payload.put("paymentIntentId", Long.toString(result.getPaymentIntentId()));
        payload.put("paymentAttemptId", Long.toString(result.getPaymentAttemptId()));
        payload.put("sourceSubmissionId", Long.toString(result.getSourceSubmissionId()));
        payload.put("entityNo", result.getEntityNo());
        payload.put("amount", PayActionActor.amountText(result.getAmount(), result.getCurrency()));
        payload.put("currency", result.getCurrency());
        payload.put("capturedAmount", PayActionActor.amountText(result.getCapturedAmount(), result.getCurrency()));
        payload.put("paymentSucceededTime", result.getSucceededAt().toString());
        payload.put("paymentMethodCode", result.getPaymentMethodCode());
        payload.put("channelId", Long.toString(result.getChannelId()));
        payload.put("channelAccountId", Long.toString(result.getChannelAccountId()));
        payload.put("midId", Long.toString(result.getMidId()));
        payload.put("channelTransactionRef", result.getChannelTransactionRef());
        return payload;
    }

    /**
     * v2 JSON Object（领域事件信封）
     * <li>v1 全部字段之后依次追加 instrumentRef、consentToSave、customerId，再追加卡属性 cardBrand、fundingType、cardLevel。</li>
     */
    public static ObjectNode json(PaymentSucceededResult result) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        fields(result).forEach(payload::put);
        payload.put("instrumentRef", result.getInstrumentRef());
        payload.put("consentToSave", result.isConsentToSave());
        payload.put("customerId", result.getCustomerId() == null ? null : Long.toString(result.getCustomerId()));
        payload.put("cardBrand", result.getCardBrand());
        payload.put("fundingType", result.getFundingType());
        payload.put("cardLevel", result.getCardLevel());
        return payload;
    }
}

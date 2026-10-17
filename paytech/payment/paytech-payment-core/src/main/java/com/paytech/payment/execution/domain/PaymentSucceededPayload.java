package com.paytech.payment.execution.domain;

import java.util.LinkedHashMap;
import java.util.Map;

import com.paytech.payment.execution.core.action.PayActionActor;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * PaymentSucceeded Payload Schema v1：旧 topic_payment_result 报文与 payment.events 新信封共用同一组字段（design/08 §3、D34）。
 * <li>固定字段顺序；ID 与金额一律十进制字符串，时间为 UTC RFC 3339，符合 Foundation JSON Object Profile。</li>
 * <li>只含最小集合，不含卡数据、付款人引用、IP 与 User-Agent；第 12 步 v2 再为 card 扩字段。</li>
 */
public final class PaymentSucceededPayload {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "PaymentSucceeded";

    /** 当前 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 聚合类型：支付尝试。 */
    public static final String AGGREGATE_TYPE = "PAYMENT_ATTEMPT";

    private PaymentSucceededPayload() {
    }

    /** 固定字段顺序的最小 Payload；金额按币种精度写成主单位十进制字符串。 */
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

    /** 同一组字段的 JSON Object，供领域事件信封使用。 */
    public static ObjectNode json(PaymentSucceededResult result) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        fields(result).forEach(payload::put);
        return payload;
    }
}

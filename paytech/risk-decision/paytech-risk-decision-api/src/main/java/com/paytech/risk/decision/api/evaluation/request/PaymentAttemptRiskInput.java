package com.paytech.risk.api.evaluation.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付尝试风控点（PAYMENT_ATTEMPT_RISK，输入合同 PAYMENT_ATTEMPT v1）的调用方输入
 * <li>由 Payment 构造后经 {@link #toRequest(String)} 生成统一评估请求；业务身份为支付尝试 ID。</li>
 * <li>ID 为正整数十进制字符串，金额为主单位十进制字符串；可选字段未知时传 null，请求中省略。</li>
 * <li>IP、User-Agent、卡指纹、消费者引用为受限值，只经内部同步请求传递，不得写入日志或 MQ。</li>
 *
 * @param paymentIntentId 支付 Intent ID
 * @param riskActionId Payment 风控 Action ID；同一支付尝试换 Action 重放返回 409
 * @param orderId 订单 ID
 * @param clientId Client ID
 * @param entityNo Operating Entity 编号，5 位大写字母或数字
 * @param paymentMethodCode 支付方式编码
 * @param captureMode AUTOMATIC 或 MANUAL
 * @param amount 金额
 * @param currency ISO 4217 币种
 * @param industryCode 行业编码
 * @param mccCode 4 位 MCC
 * @param consumerCountryCode 消费者国家 ISO 3166-1 alpha-2，可空
 * @param consumerReference Client 命名空间内的消费者（玩家）引用，可空
 * @param clientIpAddress 消费者 IP 字面量，可空
 * @param userAgent User-Agent，可空
 * @param cardFingerprint Card 生成的跨交易稳定卡指纹，非卡支付为空
 * @param cardBin 卡 BIN，6 至 8 位数字，非卡支付为空
 */
public record PaymentAttemptRiskInput(String paymentIntentId, String riskActionId, String orderId, String clientId,
    String entityNo, String paymentMethodCode, String captureMode, String amount, String currency, String industryCode,
    String mccCode, String consumerCountryCode, String consumerReference, String clientIpAddress, String userAgent,
    String cardFingerprint, String cardBin) {

    /** 风控点编码。 */
    public static final String POINT_CODE = "PAYMENT_ATTEMPT_RISK";

    /** 输入合同版本。 */
    public static final int CONTRACT_VERSION = 1;

    /** 认证要求输出编码；取值 NOT_REQUIRED 或 THREE_DS_REQUIRED。 */
    public static final String AUTHENTICATION_REQUIREMENT = "AUTHENTICATION_REQUIREMENT";

    /**
     * 生成统一评估请求
     *
     * @param paymentAttemptId 支付尝试 ID，作为业务身份
     * @return 风控点、合同版本、业务身份与非空输入字段
     */
    public EvaluateDecisionRequestBody toRequest(String paymentAttemptId) {
        EvaluateDecisionRequestBody body = new EvaluateDecisionRequestBody();
        body.setPointCode(POINT_CODE);
        body.setContractVersion(CONTRACT_VERSION);
        body.setBusinessKey(paymentAttemptId);
        body.setInputs(inputs());
        return body;
    }

    /** 按合同字段编码组装输入，省略空值。 */
    public Map<String, String> inputs() {
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "paymentIntentId", paymentIntentId);
        put(values, "riskActionId", riskActionId);
        put(values, "orderId", orderId);
        put(values, "clientId", clientId);
        put(values, "entityNo", entityNo);
        put(values, "paymentMethodCode", paymentMethodCode);
        put(values, "captureMode", captureMode);
        put(values, "amount", amount);
        put(values, "currency", currency);
        put(values, "industryCode", industryCode);
        put(values, "mccCode", mccCode);
        put(values, "consumerCountryCode", consumerCountryCode);
        put(values, "consumerReference", consumerReference);
        put(values, "clientIpAddress", clientIpAddress);
        put(values, "userAgent", userAgent);
        put(values, "cardFingerprint", cardFingerprint);
        put(values, "cardBin", cardBin);
        return values;
    }

    /** 只放入非空值。 */
    private static void put(Map<String, String> values, String key, String value) {
        if (value != null) {
            values.put(key, value);
        }
    }
}

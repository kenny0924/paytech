package com.paytech.risk.decision.domain.runtime;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.paytech.risk.control.domain.list.RiskSubjectKeys;
import com.paytech.risk.control.domain.list.RiskSubjectType;

/**
 * 一次支付尝试评估的冻结输入；字段格式已在 HTTP 入口校验，IP 字面量在构造时校验
 *
 * @param paymentAttemptId
 *            支付尝试 ID，业务唯一身份
 * @param paymentIntentId
 *            支付 Intent ID
 * @param riskActionId
 *            Payment 风控 Action ID
 * @param orderId
 *            订单 ID
 * @param clientId
 *            Client ID
 * @param entityNo
 *            Operating Entity 编号
 * @param paymentMethodCode
 *            支付方式
 * @param captureMode
 *            捕获模式
 * @param amount
 *            金额，币种主单位
 * @param currency
 *            币种
 * @param industryCode
 *            行业代码
 * @param mccCode
 *            MCC
 * @param consumerCountryCode
 *            消费者国家，可空
 * @param consumerReference
 *            消费者引用，可空
 * @param clientIpAddress
 *            消费者 IP 原值，可空；受限
 * @param userAgent
 *            User-Agent，可空；只记录
 * @param cardFingerprint
 *            卡指纹，可空
 * @param cardBin
 *            卡 BIN，可空
 */
public record PaymentRiskInput(long paymentAttemptId, long paymentIntentId, long riskActionId, long orderId,
    long clientId, String entityNo, String paymentMethodCode, String captureMode, BigDecimal amount, String currency,
    String industryCode, String mccCode, String consumerCountryCode, String consumerReference, String clientIpAddress,
    String userAgent, String cardFingerprint, String cardBin) {

    /** HTTP 合同只限制 IP 长度；在进入评估前按名单同一规则解析，非法字面量以参数错误拒绝，不产生评估。 */
    public PaymentRiskInput {
        if (clientIpAddress != null) {
            RiskSubjectKeys.normalize(RiskSubjectType.IP_ADDRESS, clientIpAddress);
        }
    }

    /**
     * 支付输入合同字段的规范值
     *
     * @return 字段键到规范值；金额为去尾零的十进制文本，缺省的可选字段不出现
     */
    public Map<String, Object> contractValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("client_id", Long.toString(clientId));
        values.put("entity_no", entityNo);
        values.put("payment_method_code", paymentMethodCode);
        values.put("capture_mode", captureMode);
        values.put("amount", amount.stripTrailingZeros().toPlainString());
        values.put("currency", currency);
        values.put("industry_code", industryCode);
        values.put("mcc_code", mccCode);
        putOptional(values, "consumer_country_code", consumerCountryCode);
        putOptional(values, "card_bin", cardBin);
        return values;
    }

    /**
     * 主体在本次评估中的规范化主体键
     *
     * @param type
     *            主体类型
     * @return 规范化主体键；本次没有该主体时为空
     */
    public String subjectKey(RiskSubjectType type) {
        String raw = switch (type) {
            case CLIENT -> Long.toString(clientId);
            case OPERATING_ENTITY -> entityNo;
            case CONSUMER ->
                consumerReference == null ? null : RiskSubjectKeys.consumer(Long.toString(clientId), consumerReference);
            case IP_ADDRESS -> clientIpAddress;
            case CARD_FINGERPRINT -> cardFingerprint;
            case CARD_BIN -> cardBin;
            case EMAIL_TOKEN, PHONE_TOKEN -> null;
        };
        return raw == null ? null : RiskSubjectKeys.normalize(type, raw);
    }

    /** 可选字段只在非空时放入。 */
    private static void putOptional(Map<String, Object> values, String key, String value) {
        if (value != null) {
            values.put(key, value);
        }
    }
}

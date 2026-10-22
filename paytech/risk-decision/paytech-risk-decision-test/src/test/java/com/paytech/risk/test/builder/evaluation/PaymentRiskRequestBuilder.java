package com.paytech.risk.test.builder.evaluation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.paytech.risk.decision.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.api.evaluation.request.PaymentAttemptRiskInput;

/**
 * 支付尝试风控点统一评估请求的测试 Builder
 * <li>{@link #template()} 为一笔合法卡支付：随机支付尝试身份（业务身份），USD 100，带消费者引用、IPv4、卡指纹、BIN
 * 与 BIN 库卡属性（发卡国 AE、VISA、CREDIT）。</li>
 * <li>字段可显式设为 null（省略）或非法值，{@link #build()} 不修复；返回的请求 inputs 可直接修改以构造重放差异。</li>
 *
 * @author Kenny
 */
public final class PaymentRiskRequestBuilder {
    private final Map<String, String> inputs = new LinkedHashMap<>();

    private String paymentAttemptId;

    private PaymentRiskRequestBuilder() { }

    /** 一笔合法卡支付。 */
    public static PaymentRiskRequestBuilder template() {
        PaymentRiskRequestBuilder builder = new PaymentRiskRequestBuilder();
        builder.paymentAttemptId = randomId();
        builder.inputs.putAll(new PaymentAttemptRiskInput(randomId(), randomId(), randomId(), "800001", "AE001",
            "CARD_VISA", "AUTOMATIC", "100.00", "USD", "DIGITAL_GOODS", "5816", "AE", "consumer-001", "203.0.113.10",
            "Mozilla/5.0 risk-test", "fp_card_" + randomId(), "411111", "AE", "VISA", "CREDIT").inputs());
        return builder;
    }

    /** 新的支付尝试与风控 Action，其余字段保持。 */
    public PaymentRiskRequestBuilder newAttempt() {
        paymentAttemptId = randomId();
        return input("riskActionId", randomId());
    }

    /** 支付尝试 ID（业务身份）。 */
    public PaymentRiskRequestBuilder paymentAttemptId(String value) {
        paymentAttemptId = value;
        return this;
    }

    /** 风控 Action ID。 */
    public PaymentRiskRequestBuilder riskActionId(String value) {
        return input("riskActionId", value);
    }

    /** Operating Entity 编号。 */
    public PaymentRiskRequestBuilder entityNo(String value) {
        return input("entityNo", value);
    }

    /** 金额。 */
    public PaymentRiskRequestBuilder amount(String value) {
        return input("amount", value);
    }

    /** 币种。 */
    public PaymentRiskRequestBuilder currency(String value) {
        return input("currency", value);
    }

    /** 消费者 IP。 */
    public PaymentRiskRequestBuilder clientIpAddress(String value) {
        return input("clientIpAddress", value);
    }

    /** User-Agent。 */
    public PaymentRiskRequestBuilder userAgent(String value) {
        return input("userAgent", value);
    }

    /** 卡指纹。 */
    public PaymentRiskRequestBuilder cardFingerprint(String value) {
        return input("cardFingerprint", value);
    }

    /** 卡 BIN；传 null 表示非卡或未知。 */
    public PaymentRiskRequestBuilder cardBin(String value) {
        return input("cardBin", value);
    }

    /** 发卡国；传 null 表示未识别或 BIN 库接入前的 Payment。 */
    public PaymentRiskRequestBuilder issuerCountryCode(String value) {
        return input("issuerCountryCode", value);
    }

    /** 卡组织；传 null 表示未识别。 */
    public PaymentRiskRequestBuilder cardBrand(String value) {
        return input("cardBrand", value);
    }

    /** 借贷记类型；传 null 表示未识别。 */
    public PaymentRiskRequestBuilder fundingType(String value) {
        return input("fundingType", value);
    }

    /** 不带卡属性三字段，等同 BIN 库接入前的 Payment 请求。 */
    public PaymentRiskRequestBuilder withoutCardAttributes() {
        return issuerCountryCode(null).cardBrand(null).fundingType(null);
    }

    /** 消费者引用。 */
    public PaymentRiskRequestBuilder consumerReference(String value) {
        return input("consumerReference", value);
    }

    /** MCC。 */
    public PaymentRiskRequestBuilder mccCode(String value) {
        return input("mccCode", value);
    }

    /** 任意输入字段，含合同外字段；null 表示省略。 */
    public PaymentRiskRequestBuilder input(String key, String value) {
        if (value == null) {
            inputs.remove(key);
        } else {
            inputs.put(key, value);
        }
        return this;
    }

    /** 新的统一评估请求；inputs 为可修改副本。 */
    public EvaluateDecisionRequestBody build() {
        EvaluateDecisionRequestBody body = new EvaluateDecisionRequestBody();
        body.setPointCode(PaymentAttemptRiskInput.POINT_CODE);
        body.setContractVersion(PaymentAttemptRiskInput.CONTRACT_VERSION);
        body.setBusinessKey(paymentAttemptId);
        body.setInputs(new LinkedHashMap<>(inputs));
        return body;
    }

    /** 15 位正整数字符串。 */
    static String randomId() {
        return Long.toString(ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 900_000_000_000_000L));
    }
}

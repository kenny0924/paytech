package com.paytech.risk.test.builder.evaluation;

import java.util.concurrent.ThreadLocalRandom;

import com.paytech.risk.api.evaluation.request.PaymentRiskAssessmentRequestBody;

/**
 * 支付尝试风控评估请求的测试 Builder
 * <li>{@link #template()} 为一笔合法卡支付：随机支付尝试身份，USD 100，带消费者引用、IPv4、卡指纹与 BIN。</li>
 * <li>字段可显式设为 null 或非法值，{@link #build()} 不修复。</li>
 *
 * @author Kenny
 */
public final class PaymentRiskRequestBuilder {
    private final PaymentRiskAssessmentRequestBody body = new PaymentRiskAssessmentRequestBody();

    private PaymentRiskRequestBuilder() { }

    /** 一笔合法卡支付。 */
    public static PaymentRiskRequestBuilder template() {
        PaymentRiskRequestBuilder builder = new PaymentRiskRequestBuilder();
        PaymentRiskAssessmentRequestBody body = builder.body;
        body.setPaymentAttemptId(randomId());
        body.setPaymentIntentId(randomId());
        body.setRiskActionId(randomId());
        body.setOrderId(randomId());
        body.setClientId("800001");
        body.setEntityNo("AE001");
        body.setPaymentMethodCode("CARD_VISA");
        body.setCaptureMode("AUTOMATIC");
        body.setAmount("100.00");
        body.setCurrency("USD");
        body.setIndustryCode("DIGITAL_GOODS");
        body.setMccCode("5816");
        body.setConsumerCountryCode("AE");
        body.setConsumerReference("consumer-001");
        body.setClientIpAddress("203.0.113.10");
        body.setUserAgent("Mozilla/5.0 risk-test");
        body.setCardFingerprint("fp_card_" + randomId());
        body.setCardBin("411111");
        return builder;
    }

    /** 新的支付尝试，其余字段保持。 */
    public PaymentRiskRequestBuilder newAttempt() {
        body.setPaymentAttemptId(randomId());
        body.setRiskActionId(randomId());
        return this;
    }

    public PaymentRiskRequestBuilder paymentAttemptId(String value) {
        body.setPaymentAttemptId(value);
        return this;
    }

    public PaymentRiskRequestBuilder riskActionId(String value) {
        body.setRiskActionId(value);
        return this;
    }

    public PaymentRiskRequestBuilder entityNo(String value) {
        body.setEntityNo(value);
        return this;
    }

    public PaymentRiskRequestBuilder amount(String value) {
        body.setAmount(value);
        return this;
    }

    public PaymentRiskRequestBuilder currency(String value) {
        body.setCurrency(value);
        return this;
    }

    public PaymentRiskRequestBuilder clientIpAddress(String value) {
        body.setClientIpAddress(value);
        return this;
    }

    public PaymentRiskRequestBuilder userAgent(String value) {
        body.setUserAgent(value);
        return this;
    }

    public PaymentRiskRequestBuilder cardFingerprint(String value) {
        body.setCardFingerprint(value);
        return this;
    }

    public PaymentRiskRequestBuilder consumerReference(String value) {
        body.setConsumerReference(value);
        return this;
    }

    public PaymentRiskRequestBuilder mccCode(String value) {
        body.setMccCode(value);
        return this;
    }

    /** 当前请求；返回同一对象，便于重放同一请求。 */
    public PaymentRiskAssessmentRequestBody build() {
        return body;
    }

    /** 15 位正整数字符串。 */
    static String randomId() {
        return Long.toString(ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 900_000_000_000_000L));
    }
}

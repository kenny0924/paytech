package com.paytech.risk.test.builder.evaluation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.paytech.risk.decision.api.evaluation.request.CheckoutPrepareRiskInput;
import com.paytech.risk.decision.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.domain.runtime.CheckoutRiskInput;

/**
 * 结账风控点统一评估请求的测试 Builder
 * <li>{@link #template()} 为一笔合法结账：随机订单 ID（业务身份），平台来源主体、无 Local Partner，USD 商品 120、折扣 20、净额 100，2 行明细。</li>
 * <li>字段可显式设为 null（省略）或非法值，{@link #build()} 不修复；返回的请求 inputs 可直接修改以构造重放差异。</li>
 *
 * @author Kenny
 */
public final class CheckoutRiskRequestBuilder {
    /** 平台来源的经营主体；Risk 没有该来源的枚举，取 CCT 合同值。 */
    public static final String PLATFORM_SOURCE = "PLATFORM";

    private final Map<String, String> inputs = new LinkedHashMap<>();

    private String orderId;

    private CheckoutRiskRequestBuilder() { }

    /** 一笔合法的平台来源结账。 */
    public static CheckoutRiskRequestBuilder template() {
        CheckoutRiskRequestBuilder builder = new CheckoutRiskRequestBuilder();
        builder.orderId = randomId();
        builder.inputs.putAll(new CheckoutPrepareRiskInput("800001", "AE001", PLATFORM_SOURCE, null, "AE", "USD",
            "120.00", "20", "100.00", "DIGITAL_GOODS", "5816", "2").inputs());
        return builder;
    }

    /** 新的订单 ID，其余字段保持。 */
    public CheckoutRiskRequestBuilder newOrder() {
        orderId = randomId();
        return this;
    }

    /** 订单 ID（业务身份）。 */
    public CheckoutRiskRequestBuilder orderId(String value) {
        orderId = value;
        return this;
    }

    /** PARTNER 来源主体及其 Local Partner。 */
    public CheckoutRiskRequestBuilder partner(String localPartnerId) {
        return entitySourceType(CheckoutRiskInput.PARTNER_SOURCE).localPartnerId(localPartnerId);
    }

    /** 经营主体来源。 */
    public CheckoutRiskRequestBuilder entitySourceType(String value) {
        return input("entitySourceType", value);
    }

    /** Local Partner ID；null 表示省略。 */
    public CheckoutRiskRequestBuilder localPartnerId(String value) {
        return input("localPartnerId", value);
    }

    /** Operating Entity 编号。 */
    public CheckoutRiskRequestBuilder entityNo(String value) {
        return input("entityNo", value);
    }

    /** 消费者国家。 */
    public CheckoutRiskRequestBuilder consumerCountryCode(String value) {
        return input("consumerCountryCode", value);
    }

    /** 币种。 */
    public CheckoutRiskRequestBuilder currency(String value) {
        return input("currency", value);
    }

    /** 商品金额、折扣金额与净额。 */
    public CheckoutRiskRequestBuilder amounts(String merchandise, String discount, String net) {
        return input("merchandiseAmount", merchandise).input("discountAmount", discount).input("netAmount", net);
    }

    /** 税前商品净额。 */
    public CheckoutRiskRequestBuilder netAmount(String value) {
        return input("netAmount", value);
    }

    /** 行业代码。 */
    public CheckoutRiskRequestBuilder industryCode(String value) {
        return input("industryCode", value);
    }

    /** MCC。 */
    public CheckoutRiskRequestBuilder mccCode(String value) {
        return input("mccCode", value);
    }

    /** 明细行数。 */
    public CheckoutRiskRequestBuilder lineCount(String value) {
        return input("lineCount", value);
    }

    /** 任意输入字段，含合同外字段；null 表示省略。 */
    public CheckoutRiskRequestBuilder input(String key, String value) {
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
        body.setPointCode(CheckoutPrepareRiskInput.POINT_CODE);
        body.setContractVersion(CheckoutPrepareRiskInput.CONTRACT_VERSION);
        body.setBusinessKey(orderId);
        body.setInputs(new LinkedHashMap<>(inputs));
        return body;
    }

    /** 15 位正整数字符串。 */
    static String randomId() {
        return Long.toString(ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 900_000_000_000_000L));
    }
}

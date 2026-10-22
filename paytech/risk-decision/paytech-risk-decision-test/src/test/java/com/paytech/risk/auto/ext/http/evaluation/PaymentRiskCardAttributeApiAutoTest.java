package com.paytech.risk.auto.ext.http.evaluation;

import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.action;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.group;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.literal;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.predicate;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.rule;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.risk.decision.api.evaluation.RiskDecisionApi;
import com.paytech.risk.decision.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.PaymentRiskRequestBuilder;
import com.paytech.risk.test.checker.evaluation.EvaluationResponseChecker.Assessed;

/**
 * 支付评估的 BIN 库卡属性输入（PAYMENT_ATTEMPT v1 原地扩展）：发卡国、卡组织、借贷记类型
 * <li>经真实配置入口用三个系统变量 PAY_INPUT_ISSUER_COUNTRY、PAY_INPUT_CARD_BRAND、PAY_INPUT_FUNDING_TYPE 保存规则并激活，
 * 发布绑定支付点版本 2（220003），再经支付评估入口执行。</li>
 * <li>拦截规则集：发卡国 NG 审核；MASTERCARD 预付卡审核。认证规则集：AMEX 要求 3DS。</li>
 * <li>带卡属性：三列冻结在交易评估快照，稳定输入摘要包含三个字段。</li>
 * <li>不带卡属性（BIN 库接入前的 Payment）：评估照常，三列为空，稳定输入摘要与扩展前的 16 字段摘要相同。</li>
 * <li>重放：同内容返回原评估；卡属性变化或缺失视为稳定输入变化，返回 409。</li>
 *
 * @see RiskDecisionApi#evaluate(EvaluateDecisionRequestBody)
 */
class PaymentRiskCardAttributeApiAutoTest extends AbstractPaymentRiskApiTest {
    /** 稳定输入摘要合同标识，与支付评估合同约定一致。 */
    private static final String STABLE_SCHEMA = "CODE-MOR:RISK:PAYMENT_ATTEMPT_STABLE_INPUT:1";

    /** 每例按真实配置入口准备两个冻结规则集、两段决策流和生效发布。 */
    @BeforeEach
    void setUpClassMocks() {
        long block = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_CARD_BLOCK")).pointCode(PAYMENT)
            .rules(
                rule("ISSUER_COUNTRY", "Issuer country", 10, action("REVIEW_PAYMENT"),
                    predicate("root", null, 1, "IN", variableOperand("PAY_INPUT_ISSUER_COUNTRY", "STRING"),
                        literal("RIGHT", 1, "STRING", "NG"))),
                rule("PREPAID_MASTERCARD", "Prepaid Mastercard", 20, action("REVIEW_PAYMENT"),
                    group("root", null, 1, "AND"),
                    predicate("brand", "root", 1, "EQ", variableOperand("PAY_INPUT_CARD_BRAND", "STRING"),
                        literal("RIGHT", 1, "STRING", "MASTERCARD")),
                    predicate("funding", "root", 2, "EQ", variableOperand("PAY_INPUT_FUNDING_TYPE", "STRING"),
                        literal("RIGHT", 1, "STRING", "PREPAID"))))
            .build());
        long auth = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_CARD_AUTH")).pointCode(PAYMENT)
            .rules(rule("AMEX_THREE_DS", "Amex 3DS", 10, action("ALLOW_PAYMENT"),
                predicate("root", null, 1, "EQ", variableOperand("PAY_INPUT_CARD_BRAND", "STRING"),
                    literal("RIGHT", 1, "STRING", "AMEX"))))
            .build());
        activeRelease(saveFlow(newCode("T_CARD_FLOW"), twoStageGraph(block, auth, rejectionReason())));
    }

    /** 卡属性未命中任何规则：放行且无需认证；三列冻结，稳定输入摘要包含卡属性。 */
    @Test
    void test_normalFlow() {
        EvaluateDecisionRequestBody request = PaymentRiskRequestBuilder.template().build();

        assess(request).assertResult(DecisionValue.ALLOW.getCode(), AuthenticationRequirement.NOT_REQUIRED.getCode());

        Map<String, Object> assessment = store.assessment(Long.parseLong(request.getBusinessKey()));
        TreeMap<String, String> stable = legacyStableFields(request);
        stable.put("issuerCountryCode", "AE");
        stable.put("cardBrand", "VISA");
        stable.put("fundingType", "CREDIT");
        assertAll(() -> assertEquals("AE", assessment.get("issuer_country_code")),
            () -> assertEquals("VISA", assessment.get("card_brand")),
            () -> assertEquals("CREDIT", assessment.get("funding_type")),
            () -> assertEquals(stableHash(stable), assessment.get("stable_input_hash")));
    }

    /** 发卡国命中审核名单国家：审核，不给认证要求。 */
    @Test
    void test_businessRuleViolation_issuerCountry() {
        assess(PaymentRiskRequestBuilder.template().issuerCountryCode("NG").build())
            .assertResult(DecisionValue.REVIEW.getCode(), null);
    }

    /** MASTERCARD 预付卡审核；同卡组织借记卡放行。 */
    @Test
    void test_businessRuleViolation_prepaidMastercard() {
        assess(PaymentRiskRequestBuilder.template().cardBrand("MASTERCARD").fundingType("PREPAID").build())
            .assertResult(DecisionValue.REVIEW.getCode(), null);
        assess(PaymentRiskRequestBuilder.template().cardBrand("MASTERCARD").fundingType("DEBIT").build())
            .assertResult(DecisionValue.ALLOW.getCode(), AuthenticationRequirement.NOT_REQUIRED.getCode());
    }

    /** AMEX 命中认证规则集：放行并要求 3DS。 */
    @Test
    void test_edgeCase_cardBrandThreeDs() {
        assess(PaymentRiskRequestBuilder.template().cardBrand("AMEX").build())
            .assertResult(DecisionValue.ALLOW.getCode(), AuthenticationRequirement.THREE_DS_REQUIRED.getCode());
    }

    /** 不带卡属性的请求：放行，三列为空，规则比较为假；稳定输入摘要与扩展前的 16 字段摘要一致。 */
    @Test
    void test_noDataFound_withoutCardAttributes() {
        EvaluateDecisionRequestBody request = PaymentRiskRequestBuilder.template().withoutCardAttributes().build();

        assess(request).assertResult(DecisionValue.ALLOW.getCode(), AuthenticationRequirement.NOT_REQUIRED.getCode());

        Map<String, Object> assessment = store.assessment(Long.parseLong(request.getBusinessKey()));
        assertAll(() -> assertNull(assessment.get("issuer_country_code")),
            () -> assertNull(assessment.get("card_brand")),
            () -> assertNull(assessment.get("funding_type")),
            () -> assertEquals(stableHash(legacyStableFields(request)), assessment.get("stable_input_hash")));
    }

    /** 同内容重放返回原评估；改卡组织或去掉卡属性返回 409，原评估不变。 */
    @Test
    void test_edgeCase_replay() {
        EvaluateDecisionRequestBody request = PaymentRiskRequestBuilder.template().build();
        Assessed first = assess(request).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.NOT_REQUIRED.getCode());

        assertEquals(first, assess(request).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.NOT_REQUIRED.getCode()));
        request.getInputs().put("cardBrand", "MASTERCARD");
        assess(request).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode());
        request.getInputs().remove("cardBrand");
        request.getInputs().remove("issuerCountryCode");
        request.getInputs().remove("fundingType");
        assess(request).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode());

        assertAll(() -> assertEquals(1, store.countPrimaryEvaluations()),
            () -> assertEquals("VISA", store.assessment(Long.parseLong(request.getBusinessKey())).get("card_brand")));
    }

    /** 扩展前的 16 个稳定输入字段：业务身份与请求中的原值，金额去尾零，可选字段缺省为 null。 */
    private static TreeMap<String, String> legacyStableFields(EvaluateDecisionRequestBody request) {
        Map<String, String> inputs = request.getInputs();
        TreeMap<String, String> values = new TreeMap<>();
        values.put("paymentAttemptId", request.getBusinessKey());
        for (String key : new String[] {"paymentIntentId", "riskActionId", "orderId", "clientId", "entityNo",
            "paymentMethodCode", "captureMode", "currency", "industryCode", "mccCode", "consumerCountryCode",
            "consumerReference", "cardFingerprint", "cardBin"}) {
            values.put(key, inputs.get(key));
        }
        values.put("amount", new BigDecimal(inputs.get("amount")).stripTrailingZeros().toPlainString());
        return values;
    }

    /** 按 RFC 8785 对只含 ASCII 字符串与 null 的对象排序键后取 SHA-256 小写十六进制。 */
    private static String stableHash(TreeMap<String, String> fields) throws Exception {
        TreeMap<String, String> object = new TreeMap<>(fields);
        object.put("schema", STABLE_SCHEMA);
        StringBuilder json = new StringBuilder("{");
        object.forEach((key, value) -> json.append(json.length() == 1 ? "" : ",").append('"').append(key)
            .append("\":").append(value == null ? "null" : "\"" + value + "\""));
        json.append('}');
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(json.toString().getBytes(StandardCharsets.UTF_8)));
    }
}

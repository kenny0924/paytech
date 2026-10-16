package com.paytech.risk.auto.ext.http.evaluation;

import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.risk.api.evaluation.RiskDecisionApi;
import com.paytech.risk.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.decision.domain.enums.RuleTraceType;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.decision.domain.runtime.CheckoutRiskInput;
import com.paytech.risk.test.base.AbstractCheckoutRiskApiTest;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.CheckoutRiskRequestBuilder;
import com.paytech.risk.test.checker.evaluation.EvaluationResponseChecker.Assessed;

/**
 * 结账风控评估：真实配置入口准备结账拦截流程并激活，再经统一评估入口执行
 * <li>拦截规则集：消费者国家为受限国家拒绝；Local Partner 被拦截拒绝；行业为 GAMING 且净额超过 1000 审核；明细超过 50 行审核。</li>
 * <li>正常流程：放行；落库评估、结账输入快照（订单与商业事实、稳定输入摘要）、规则与流程轨迹，没有 Feature 结果。</li>
 * <li>业务规则：国家拒绝、行业与净额组合审核、明细行数审核、Local Partner 拒绝；可选 Local Partner 缺省时比较不成立。</li>
 * <li>重放：同一订单同内容返回原结果且不重复写入，金额尾零不影响比较；输入变化返回 409。</li>
 *
 * @see RiskDecisionApi#evaluate(EvaluateDecisionRequestBody)
 */
class CheckoutRiskAssessmentApiAutoTest extends AbstractCheckoutRiskApiTest {
    /** 被拦截的 Local Partner。 */
    private static final String BLOCKED_PARTNER = "399999";

    /** 每例按真实配置入口准备拦截规则集、结账流程和生效发布。 */
    @BeforeEach
    void setUpClassMocks() {
        String reason = rejectionReason();
        long block = frozenRuleSet(RuleSetCreateRequestBuilder.template(checkoutCode("BLOCK")).pointCode(CHECKOUT)
            .rules(
                rule("RESTRICTED_COUNTRY", "RESTRICTED_COUNTRY", 10, reason, DENY_MESSAGE, action(DENY), List.of(),
                    predicate("root", null, 1, "IN", variable("LEFT", 1, "INPUT_COUNTRY_CODE", "STRING"),
                        literal("RIGHT", 1, "STRING", "KP"), literal("RIGHT", 2, "STRING", "IR"))),
                rule("BLOCKED_PARTNER", "BLOCKED_PARTNER", 20, reason, DENY_MESSAGE, action(DENY), List.of(),
                    predicate("root", null, 1, "EQ", variable("LEFT", 1, "INPUT_LOCAL_PARTNER_ID", "STRING"),
                        literal("RIGHT", 1, "STRING", BLOCKED_PARTNER))),
                rule("GAMING_LARGE", "GAMING_LARGE", 30, action(REVIEW), group("all", null, 1, "AND"),
                    predicate("industry", "all", 1, "EQ", variable("LEFT", 1, "INPUT_INDUSTRY_CODE", "STRING"),
                        literal("RIGHT", 1, "STRING", "GAMING")),
                    predicate("net", "all", 2, "GT", variable("LEFT", 1, "INPUT_NET_AMOUNT", "NUMBER"), money("1000"))),
                rule("MANY_LINES", "MANY_LINES", 40, action(REVIEW),
                    predicate("root", null, 1, "GT", variableOperand("INPUT_LINE_COUNT", "NUMBER"), scalar("50"))))
            .build());
        activeRelease(saveFlow(checkoutCode("FLOW"), blockGraph(block, reason)));
    }

    /** 未命中任何规则：放行；评估、结账输入快照、规则轨迹与流程轨迹完整落库，没有 Feature 结果。 */
    @Test
    void test_normalFlow() {
        EvaluateDecisionRequestBody request = CheckoutRiskRequestBuilder.template().build();

        Assessed assessed = assess(request).assertResult(DecisionValue.ALLOW.getCode());

        Map<String, Object> evaluation = store.evaluation(assessed.evaluationId());
        Map<String, Object> snapshot = store.snapshot(Long.parseLong(request.getBusinessKey()));
        assertAll("落库事实", () -> assertEquals(EvaluationMode.PRIMARY.getCode(), evaluation.get("evaluation_mode")),
            () -> assertEquals(TechnicalStatus.SUCCEEDED.getCode(), evaluation.get("technical_status")),
            () -> assertEquals(DecisionValue.ALLOW.getCode(), evaluation.get("business_decision")),
            () -> assertEquals(CHECKOUT + ":" + request.getBusinessKey(), evaluation.get("evaluation_request_id")),
            () -> assertEquals(snapshot.get("stable_input_hash"), evaluation.get("input_snapshot_hash"), "输入摘要"),
            () -> assertEquals(assessed.evaluationId(), ((Number)snapshot.get("evaluation_id")).longValue()),
            () -> assertEquals(800001L, ((Number)snapshot.get("client_id")).longValue()),
            () -> assertEquals(request.getInputs().get("entityNo"), snapshot.get("entity_no")),
            () -> assertEquals(CheckoutRiskRequestBuilder.PLATFORM_SOURCE, snapshot.get("entity_source_type")),
            () -> assertNull(snapshot.get("local_partner_id")),
            () -> assertEquals("AE", snapshot.get("consumer_country_code")),
            () -> assertEquals("USD", snapshot.get("currency")),
            () -> assertEquals(0, new BigDecimal("120").compareTo((BigDecimal)snapshot.get("merchandise_amount"))),
            () -> assertEquals(0, new BigDecimal("20").compareTo((BigDecimal)snapshot.get("discount_amount"))),
            () -> assertEquals(0, new BigDecimal("100").compareTo((BigDecimal)snapshot.get("net_amount"))),
            () -> assertEquals("DIGITAL_GOODS", snapshot.get("industry_code")),
            () -> assertEquals("5816", snapshot.get("mcc_code")),
            () -> assertEquals(2, ((Number)snapshot.get("line_count")).intValue()),
            () -> assertTrue(((String)snapshot.get("stable_input_hash")).matches("[0-9a-f]{64}"), "稳定输入摘要"),
            () -> assertEquals(0, store.countFeatureResults(assessed.evaluationId()), "结账不计算 Feature"),
            () -> assertEquals(4, store.traces(assessed.evaluationId(), RuleTraceType.RULE.getCode()).size(), "规则轨迹"),
            () -> assertEquals(5, store.traces(assessed.evaluationId(), RuleTraceType.PREDICATE.getCode()).size(),
                "谓词轨迹"),
            () -> assertEquals(List.of("start", "block", "allow"), store.flowNodeKeys(assessed.evaluationId()), "流程轨迹"));
    }

    /** 消费者国家为受限国家：拒绝，首个终态命中即结束本规则集。 */
    @Test
    void test_businessRuleViolation_restrictedCountry() {
        EvaluateDecisionRequestBody request = CheckoutRiskRequestBuilder.template().consumerCountryCode("KP").build();

        Assessed assessed = assess(request).assertResult(DecisionValue.DENY.getCode());

        assertAll(() -> assertEquals(List.of("start", "block", "deny"), store.flowNodeKeys(assessed.evaluationId())),
            () -> assertEquals(1, store.traces(assessed.evaluationId(), RuleTraceType.RULE.getCode()).size(),
                "终态命中后不再执行后续规则"),
            () -> assertEquals("KP", store.snapshot(Long.parseLong(request.getBusinessKey())).get("consumer_country_code")));
    }

    /** 行业为 GAMING 且净额超过 1000：审核；同行业小额放行。 */
    @Test
    void test_businessRuleViolation_industryAndAmount() {
        assess(CheckoutRiskRequestBuilder.template().industryCode("GAMING").amounts("1500", "0", "1500").build())
            .assertResult(DecisionValue.REVIEW.getCode());
        assess(CheckoutRiskRequestBuilder.template().industryCode("GAMING").build())
            .assertResult(DecisionValue.ALLOW.getCode());
    }

    /** 明细超过 50 行：审核；恰好 50 行放行。 */
    @Test
    void test_businessRuleViolation_lineCount() {
        assess(CheckoutRiskRequestBuilder.template().lineCount("51").build()).assertResult(DecisionValue.REVIEW.getCode());
        assess(CheckoutRiskRequestBuilder.template().lineCount("50").build()).assertResult(DecisionValue.ALLOW.getCode());
    }

    /** Local Partner：被拦截的拒绝，其他放行并冻结到快照；平台来源没有 Local Partner，比较不成立。 */
    @Test
    void test_edgeCase_localPartner() {
        EvaluateDecisionRequestBody partner = CheckoutRiskRequestBuilder.template().partner("300001").build();

        assess(partner).assertResult(DecisionValue.ALLOW.getCode());
        assess(CheckoutRiskRequestBuilder.template().partner(BLOCKED_PARTNER).build())
            .assertResult(DecisionValue.DENY.getCode());
        assess(CheckoutRiskRequestBuilder.template().build()).assertResult(DecisionValue.ALLOW.getCode());

        Map<String, Object> snapshot = store.snapshot(Long.parseLong(partner.getBusinessKey()));
        assertAll(() -> assertEquals(CheckoutRiskInput.PARTNER_SOURCE, snapshot.get("entity_source_type")),
            () -> assertEquals(300001L, ((Number)snapshot.get("local_partner_id")).longValue()));
    }

    /** 同一订单重放：返回原评估且不新增评估与快照；金额尾零不同视为同一内容。 */
    @Test
    void test_edgeCase_replay() {
        EvaluateDecisionRequestBody request = CheckoutRiskRequestBuilder.template().build();
        Assessed first = assess(request).assertResult(DecisionValue.ALLOW.getCode());

        request.getInputs().put("netAmount", "100");
        request.getInputs().put("merchandiseAmount", "120.0000");
        Assessed replayed = assess(request).assertResult(DecisionValue.ALLOW.getCode());

        assertAll(() -> assertEquals(first, replayed), () -> assertEquals(1, store.countPrimaryEvaluations()),
            () -> assertEquals(1, store.countSnapshots()));
    }

    /** 同一订单改输入：409，原评估不变。 */
    @Test
    void test_businessRuleViolation_replayConflict() {
        EvaluateDecisionRequestBody request = CheckoutRiskRequestBuilder.template().build();
        assess(request).assertResult(DecisionValue.ALLOW.getCode());

        request.getInputs().put("netAmount", "100.01");
        assess(request).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode());
        assess(CheckoutRiskRequestBuilder.template().orderId(request.getBusinessKey()).consumerCountryCode("KP").build())
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode());

        assertEquals(1, store.countPrimaryEvaluations());
        assertEquals(0, new BigDecimal("100").compareTo(
            (BigDecimal)store.snapshot(Long.parseLong(request.getBusinessKey())).get("net_amount")), "首次冻结的净额不变");
    }
}

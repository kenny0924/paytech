package com.paytech.risk.auto.ext.http.evaluation;

import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleRequestBody;
import com.paytech.risk.api.evaluation.RiskDecisionApi;
import com.paytech.risk.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.PaymentRiskRequestBuilder;
import com.paytech.risk.test.checker.evaluation.PaymentRiskResponseChecker.Assessed;

/**
 * 支付尝试风控评估：真实配置入口准备两段决策流并激活，再经支付评估入口执行
 * <li>拦截规则集：IP 命中黑名单拒绝；同卡 10 分钟内已有 2 次评估拒绝；金额超过 1000 审核。认证规则集：金额超过 500 要求 3DS。</li>
 * <li>正常流程：放行（无需认证）、放行并要求 3DS；落库评估、交易评估快照、Feature 结果、规则轨迹与累计事件。</li>
 * <li>业务规则：名单命中（含 IPv6 同 /64）、实时累计命中、金额审核；审核与拒绝写入拒绝事件。</li>
 * <li>重放：同一支付尝试同内容返回原结果且不重复写入；IP 与 User-Agent 变化不影响重放；风控 Action 或稳定输入变化返回 409。</li>
 *
 * @see RiskDecisionApi#evaluate(EvaluateDecisionRequestBody)
 */
class PaymentRiskAssessmentApiAutoTest extends AbstractPaymentRiskApiTest {
    private String blockedIpList;

    /** 每例按真实配置入口准备名单、变量、两个冻结规则集、决策流和生效发布。 */
    @BeforeEach
    void setUpClassMocks() {
        String reason = rejectionReason();
        blockedIpList = newCode("T_BLOCKED_IP");
        createList(blockedIpList, RiskSubjectType.IP_ADDRESS.getCode(), "BLOCK");
        String ipBlocked = newCode("T_IP_BLOCKED");
        createListVariable(ipBlocked, blockedIpList, RiskSubjectType.IP_ADDRESS.getCode());
        String cardCount = newCode("T_CARD_COUNT_10M");
        createVelocityVariable(cardCount, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode(),
            RiskSubjectType.CARD_FINGERPRINT.getCode(), VelocityMetric.COUNT.getCode(), null, 600, false);
        long block = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_BLOCK")).pointCode(PAYMENT)
            .rules(
                denyRule("BLOCKED_IP", 10, reason,
                    predicate("root", null, 1, "EQ", variableOperand(ipBlocked, "BOOLEAN"), bool(true))),
                denyRule("CARD_FREQUENCY", 20, reason,
                    predicate("root", null, 1, "GTE", variableOperand(cardCount, "NUMBER"), scalar("2"))),
                rule("LARGE_AMOUNT", "Large amount", 30, action("REVIEW_PAYMENT"),
                    predicate("root", null, 1, "GT", variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money("1000"))))
            .build());
        long auth = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_AUTH")).pointCode(PAYMENT)
            .rules(rule("THREE_DS_AMOUNT", "3DS amount", 10, action("ALLOW_PAYMENT"),
                predicate("root", null, 1, "GT", variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money("500"))))
            .build());
        activeRelease(saveFlow(newCode("T_FLOW"), twoStageGraph(block, auth, reason)));
    }

    /** 未命中任何规则：放行且无需认证；评估、快照、Feature 结果、规则轨迹与评估事件完整落库。 */
    @Test
    void test_normalFlow() {
        EvaluateDecisionRequestBody request = PaymentRiskRequestBuilder.template().build();

        Assessed assessed = assess(request).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.NOT_REQUIRED.getCode());

        Map<String, Object> evaluation = store.evaluation(assessed.evaluationId());
        Map<String, Object> assessment = store.assessment(Long.parseLong(request.getBusinessKey()));
        assertAll("落库事实", () -> assertEquals(EvaluationMode.PRIMARY.getCode(), evaluation.get("evaluation_mode")),
            () -> assertEquals(TechnicalStatus.SUCCEEDED.getCode(), evaluation.get("technical_status")),
            () -> assertEquals(DecisionValue.ALLOW.getCode(), evaluation.get("business_decision")),
            () -> assertEquals("PAYMENT_ATTEMPT_RISK:" + request.getBusinessKey(),
                evaluation.get("evaluation_request_id")),
            () -> assertEquals(store.assessmentIdOf(assessed.evaluationId()), ((Number)assessment.get("assessment_id")).longValue()),
            () -> assertEquals(request.getInputs().get("entityNo"), assessment.get("entity_no")),
            () -> assertEquals(0, new BigDecimal("100").compareTo((BigDecimal)assessment.get("amount"))),
            () -> assertEquals(request.getInputs().get("clientIpAddress"), assessment.get("client_ip_address")),
            () -> assertEquals(request.getInputs().get("cardBin"), assessment.get("card_bin")),
            () -> assertEquals(AuthenticationRequirement.NOT_REQUIRED.getCode(),
                assessment.get("authentication_requirement")),
            () -> assertEquals(2, store.featureResults(assessed.evaluationId()).size(), "Feature 结果"),
            () -> assertEquals(4, store.traces(assessed.evaluationId(), RuleTraceType.RULE.getCode()).size(), "规则轨迹"),
            () -> assertEquals(4, store.traces(assessed.evaluationId(), RuleTraceType.PREDICATE.getCode()).size(),
                "谓词轨迹"),
            () -> assertEquals(List.of(VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode()),
                store.velocityEventCodes(store.assessmentIdOf(assessed.evaluationId()))),
            () -> assertEquals(
                List.of(RiskSubjectType.CARD_FINGERPRINT.getCode(), RiskSubjectType.CLIENT.getCode(),
                    RiskSubjectType.CONSUMER.getCode(), RiskSubjectType.IP_ADDRESS.getCode(),
                    RiskSubjectType.OPERATING_ENTITY.getCode()),
                store.velocitySubjects(store.assessmentIdOf(assessed.evaluationId()), VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode())
                    .stream().map(row -> row.get("subject_type")).toList()));
    }

    /** 认证规则集命中：放行并要求 3DS。 */
    @Test
    void test_normalFlow_threeDs() {
        EvaluateDecisionRequestBody request = PaymentRiskRequestBuilder.template().amount("600").build();

        Assessed assessed = assess(request).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.THREE_DS_REQUIRED.getCode());

        assertEquals(AuthenticationRequirement.THREE_DS_REQUIRED.getCode(),
            store.assessment(Long.parseLong(request.getBusinessKey())).get("authentication_requirement"));
        assertEquals(List.of(VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode()),
            store.velocityEventCodes(store.assessmentIdOf(assessed.evaluationId())));
    }

    /** 金额超过阈值：审核，不给认证要求，同时写入拒绝事件；认证规则集不执行。 */
    @Test
    void test_businessRuleViolation_review() {
        EvaluateDecisionRequestBody request = PaymentRiskRequestBuilder.template().amount("1500").build();

        Assessed assessed = assess(request).assertResult(DecisionValue.REVIEW.getCode(), null);

        assertAll(
            () -> assertNull(
                store.assessment(Long.parseLong(request.getBusinessKey())).get("authentication_requirement")),
            () -> assertEquals(
                List.of(VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode(),
                    VelocityEventCode.PAYMENT_ATTEMPT_REJECTED.getCode()),
                store.velocityEventCodes(store.assessmentIdOf(assessed.evaluationId()))),
            () -> assertEquals(3, store.traces(assessed.evaluationId(), RuleTraceType.RULE.getCode()).size(),
                "只执行拦截规则集"));
    }

    /** IP 命中黑名单：拒绝；IPv6 同一 /64 网段的其他地址同样命中；本次没有 IP 时不命中。 */
    @Test
    void test_businessRuleViolation_blockedIp() {
        addListEntry(blockedIpList, "203.0.113.10");
        addListEntry(blockedIpList, "2001:db8:abcd:12::1");

        assess(PaymentRiskRequestBuilder.template().build()).assertResult(DecisionValue.DENY.getCode(), null);
        assess(PaymentRiskRequestBuilder.template().clientIpAddress("2001:db8:abcd:12:ffff::9").build())
            .assertResult(DecisionValue.DENY.getCode(), null);
        assess(PaymentRiskRequestBuilder.template().clientIpAddress("203.0.113.11").build())
            .assertResult(DecisionValue.ALLOW.getCode(), AuthenticationRequirement.NOT_REQUIRED.getCode());
        assess(PaymentRiskRequestBuilder.template().clientIpAddress(null).build())
            .assertResult(DecisionValue.ALLOW.getCode(), AuthenticationRequirement.NOT_REQUIRED.getCode());
    }

    /** 同卡 10 分钟内已有 2 次评估：第三次拒绝；计数不含本次，换卡不受影响。 */
    @Test
    void test_businessRuleViolation_cardFrequency() {
        PaymentRiskRequestBuilder card = PaymentRiskRequestBuilder.template();

        assess(card.newAttempt().build()).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.NOT_REQUIRED.getCode());
        assess(card.newAttempt().build()).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.NOT_REQUIRED.getCode());
        assess(card.newAttempt().build()).assertResult(DecisionValue.DENY.getCode(), null);
        assess(PaymentRiskRequestBuilder.template().cardFingerprint("fp_card_other_0001").build())
            .assertResult(DecisionValue.ALLOW.getCode(), AuthenticationRequirement.NOT_REQUIRED.getCode());
    }

    /** 同一支付尝试重放：返回原评估，不新增评估与累计事件；IP 与 User-Agent 变化不参与比较。 */
    @Test
    void test_edgeCase_replay() {
        EvaluateDecisionRequestBody request = PaymentRiskRequestBuilder.template().build();
        Assessed first = assess(request).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.NOT_REQUIRED.getCode());

        request.getInputs().put("clientIpAddress", "198.51.100.7");
        request.getInputs().put("userAgent", "another-agent");
        Assessed replayed = assess(request).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.NOT_REQUIRED.getCode());

        assertAll(() -> assertEquals(first, replayed), () -> assertEquals(1, store.countPrimaryEvaluations()),
            () -> assertEquals(1, store.velocityEventCodes(store.assessmentIdOf(first.evaluationId())).size()),
            () -> assertEquals("203.0.113.10",
                store.assessment(Long.parseLong(request.getBusinessKey())).get("client_ip_address"),
                "首次冻结的 IP 不变"));
    }

    /** 同一支付尝试换风控 Action 或改稳定输入：409，原评估不变。 */
    @Test
    void test_businessRuleViolation_replayConflict() {
        EvaluateDecisionRequestBody request = PaymentRiskRequestBuilder.template().build();
        assess(request).assertResult(DecisionValue.ALLOW.getCode(), AuthenticationRequirement.NOT_REQUIRED.getCode());

        assess(PaymentRiskRequestBuilder.template().paymentAttemptId(request.getBusinessKey()).build())
            .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode());
        request.getInputs().put("amount", "100.01");
        assess(request).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode());

        assertEquals(1, store.countPrimaryEvaluations());
    }

    /** 拒绝规则：命中即结束本规则集，附原因编码与文案。 */
    private static RuleRequestBody denyRule(String code, int priority, String reason,
        com.paytech.risk.api.decision.DecisionRuleSetManagementModels.ConditionNodeRequestBody condition) {
        return rule(code, code, priority, reason, "支付被风控拒绝", action("DENY_PAYMENT"), List.of(), condition);
    }
}

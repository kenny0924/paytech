package com.paytech.risk.auto.ext.http.evaluation;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.decision.api.enums.RiskDecisionEvaluationErrorCode.RISK_DECISION_NOT_CONFIGURED;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.risk.decision.api.evaluation.RiskDecisionApi;
import com.paytech.risk.decision.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.api.evaluation.request.PaymentAttemptRiskInput;
import com.paytech.risk.control.core.enums.RiskListPurpose;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.decision.domain.runtime.EvaluationInputContract;
import com.paytech.risk.decision.domain.runtime.EvaluationInputContract.Field;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.PaymentRiskRequestBuilder;
import com.paytech.risk.test.checker.evaluation.EvaluationResponseChecker.Assessed;

/**
 * 支付尝试风控评估的可用性与输入边界
 * <li>无数据：本环境没有生效发布返回 503 RISK_DECISION_NOT_CONFIGURED，不写评估。</li>
 * <li>参数：业务身份或输入字段缺失、未知、格式不符或标识超过 BIGINT 上限返回 422 并指出字段；风控点或合同版本未登记、IP 不是合法字面量返回 400；均先于发布查询，不写评估。</li>
 * <li>合同一致：评估合同漏登规则输入字段或必填不一致时无法构造，工程漂移在启动时暴露。</li>
 * <li>超时：执行耗时超过冻结的决策点版本超时记为 TIMED_OUT 并落库，不写累计事件；重放返回原结果。</li>
 * <li>技术失败：生效发布引用的变量损坏时返回 FAILED 并落库失败评估，不写累计事件；同一支付尝试重放返回原失败结果。</li>
 *
 * @see RiskDecisionApi#evaluate(EvaluateDecisionRequestBody)
 */
class PaymentRiskAssessmentAvailabilityApiAutoTest extends AbstractPaymentRiskApiTest {

    /** 没有生效发布：503，不写评估。 */
    @Test
    void test_noDataFound_notConfigured() {
        assess(PaymentRiskRequestBuilder.template().build()).assertProblem(503, RISK_DECISION_NOT_CONFIGURED.getCode());

        assertEquals(0, store.countPrimaryEvaluations());
    }

    /** 业务身份或输入字段缺失、未知或格式不符：422 并指出字段。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("contractViolations")
    void test_invalidParameter_contract(String scenario, String field, UnaryOperator<PaymentRiskRequestBuilder> change) {
        assess(change.apply(PaymentRiskRequestBuilder.template()).build()).assertProblem(422,
            VALIDATION_ERROR.getCode()).assertFieldError(field);

        assertEquals(0, store.countPrimaryEvaluations());
    }

    /** 执行超过决策点版本超时：TIMED_OUT 落库，不形成业务决定与累计事件，重放返回原结果。 */
    @Test
    void test_businessRuleViolation_timedOut() {
        String reason = rejectionReason();
        long block = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_BLOCK")).pointCode(PAYMENT)
            .rules(rule("LARGE_AMOUNT", "Large amount", 10, action("REVIEW_PAYMENT"),
                predicate("root", null, 1, "GT", variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money("1000"))))
            .build());
        long auth = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_AUTH")).pointCode(PAYMENT)
            .rules(rule("THREE_DS_AMOUNT", "3DS amount", 10, action("ALLOW_PAYMENT"),
                predicate("root", null, 1, "GT", variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money("500"))))
            .build());
        activeRelease(saveFlow(newCode("T_FLOW"), twoStageGraph(block, auth, reason)));
        payments.mockEvaluationTimeout();
        EvaluateDecisionRequestBody request = PaymentRiskRequestBuilder.template().build();

        Assessed timedOut = assess(request).assertResult(TechnicalStatus.TIMED_OUT.getCode(), null);

        Map<String, Object> evaluation = store.evaluation(timedOut.evaluationId());
        assertAll("超时评估", () -> assertEquals(TechnicalStatus.TIMED_OUT.getCode(), evaluation.get("technical_status")),
            () -> assertEquals("EVALUATION_TIMEOUT", evaluation.get("failure_code")),
            () -> assertNull(evaluation.get("business_decision")),
            () -> assertNull(
                store.assessment(Long.parseLong(request.getBusinessKey())).get("authentication_requirement")),
            () -> assertEquals(List.of(), store.velocityEventCodes(store.assessmentIdOf(timedOut.evaluationId()))));
        assertEquals(timedOut, assess(request).assertResult(TechnicalStatus.TIMED_OUT.getCode(), null));
        assertEquals(1, store.countPrimaryEvaluations());
    }

    /** 评估合同必须覆盖决策点合同中的规则输入字段且必填一致。 */
    @Test
    void test_edgeCase_contractDrift() {
        Pattern any = Pattern.compile(".+");
        String point = PaymentAttemptRiskInput.POINT_CODE;

        IllegalStateException missing = assertThrows(IllegalStateException.class,
            () -> new EvaluationInputContract(point, 1, any, List.of(new Field("entityNo", true, any, 5))));
        assertEquals("评估合同缺少规则输入字段: " + point + ".clientId", missing.getMessage());

        IllegalStateException required = assertThrows(IllegalStateException.class,
            () -> new EvaluationInputContract(point, 1, any, List.of(new Field("clientId", false, any, 19))));
        assertEquals("评估合同与规则输入字段必填不一致: " + point + ".clientId", required.getMessage());
    }

    /** 风控点或输入合同版本未登记：400。 */
    @Test
    void test_invalidParameter_unregistered() {
        EvaluateDecisionRequestBody unknownPoint = PaymentRiskRequestBuilder.template().build();
        unknownPoint.setPointCode("CHECKOUT_PREPARE");
        EvaluateDecisionRequestBody unknownVersion = PaymentRiskRequestBuilder.template().build();
        unknownVersion.setContractVersion(2);

        assess(unknownPoint).assertProblem(400, INVALID_REQUEST.getCode());
        assess(unknownVersion).assertProblem(400, INVALID_REQUEST.getCode());

        assertEquals(0, store.countPrimaryEvaluations());
    }

    /** IP 不是合法字面量：先于发布查询拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidIps")
    void test_invalidParameter_ip(String ip) {
        assess(PaymentRiskRequestBuilder.template().clientIpAddress(ip).build()).assertProblem(400,
            INVALID_REQUEST.getCode());

        assertEquals(0, store.countPrimaryEvaluations());
    }

    /** 生效发布引用的名单变量损坏：FAILED 落库、无认证要求、无累计事件；重放返回原结果。 */
    @Test
    void test_businessRuleViolation_technicalFailure() {
        String list = newCode("T_LIST");
        createList(list, RiskSubjectType.IP_ADDRESS.getCode(), RiskListPurpose.BLOCK.getCode());
        String variable = newCode("T_IP_BLOCKED");
        createListVariable(variable, list, RiskSubjectType.IP_ADDRESS.getCode());
        String reason = rejectionReason();
        long block = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_BLOCK")).pointCode(PAYMENT)
            .rules(rule("BLOCKED_IP", "BLOCKED_IP", 10, reason, "支付被风控拒绝", action("DENY_PAYMENT"), List.of(),
                predicate("root", null, 1, "EQ", variableOperand(variable, "BOOLEAN"), bool(true))))
            .build());
        long auth = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_AUTH")).pointCode(PAYMENT)
            .rules(rule("THREE_DS_AMOUNT", "3DS amount", 10, action("ALLOW_PAYMENT"),
                predicate("root", null, 1, "GT", variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money("500"))))
            .build());
        activeRelease(saveFlow(newCode("T_FLOW"), twoStageGraph(block, auth, reason)));
        store.markVariableDeleted(variable);
        EvaluateDecisionRequestBody request = PaymentRiskRequestBuilder.template().build();

        Assessed failed = assess(request).assertResult(TechnicalStatus.FAILED.getCode(), null);

        Map<String, Object> evaluation = store.evaluation(failed.evaluationId());
        assertAll("失败评估", () -> assertEquals(EvaluationMode.PRIMARY.getCode(), evaluation.get("evaluation_mode")),
            () -> assertEquals(TechnicalStatus.FAILED.getCode(), evaluation.get("technical_status")),
            () -> assertEquals("EVALUATION_ERROR", evaluation.get("failure_code")),
            () -> assertNull(evaluation.get("business_decision")),
            () -> assertNull(
                store.assessment(Long.parseLong(request.getBusinessKey())).get("authentication_requirement")),
            () -> assertEquals(List.of(), store.velocityEventCodes(store.assessmentIdOf(failed.evaluationId()))));
        assertEquals(failed, assess(request).assertResult(TechnicalStatus.FAILED.getCode(), null));
        assertEquals(1, store.countPrimaryEvaluations());
    }

    static Stream<Arguments> contractViolations() {
        return Stream.of(Arguments.of("缺少支付尝试", "businessKey", change(builder -> builder.paymentAttemptId(null))),
            Arguments.of("支付尝试非正整数", "businessKey", change(builder -> builder.paymentAttemptId("0"))),
            Arguments.of("支付尝试超过 BIGINT 上限", "businessKey",
                change(builder -> builder.paymentAttemptId("9223372036854775808"))),
            Arguments.of("风控 Action 超过 BIGINT 上限", "inputs.riskActionId",
                change(builder -> builder.riskActionId("9999999999999999999"))),
            Arguments.of("缺少金额", "inputs.amount", change(builder -> builder.amount(null))),
            Arguments.of("未知字段", "inputs.deviceId", change(builder -> builder.input("deviceId", "d-1"))),
            Arguments.of("风控 Action 非数字", "inputs.riskActionId", change(builder -> builder.riskActionId("abc"))),
            Arguments.of("Operating Entity 小写", "inputs.entityNo", change(builder -> builder.entityNo("ae001"))),
            Arguments.of("金额为零", "inputs.amount", change(builder -> builder.amount("0.00"))),
            Arguments.of("金额超过 8 位小数", "inputs.amount", change(builder -> builder.amount("1.123456789"))),
            Arguments.of("币种小写", "inputs.currency", change(builder -> builder.currency("usd"))),
            Arguments.of("MCC 非 4 位", "inputs.mccCode", change(builder -> builder.mccCode("58161"))),
            Arguments.of("卡指纹过短", "inputs.cardFingerprint", change(builder -> builder.cardFingerprint("short"))),
            Arguments.of("IP 超长", "inputs.clientIpAddress", change(builder -> builder.clientIpAddress("1".repeat(46)))));
    }

    static Stream<String> invalidIps() {
        return Stream.of("not-an-ip", "999.1.1.1", "2001:db8::zz");
    }

    /** 参数化场景的请求改写。 */
    private static UnaryOperator<PaymentRiskRequestBuilder> change(UnaryOperator<PaymentRiskRequestBuilder> change) {
        return change;
    }
}

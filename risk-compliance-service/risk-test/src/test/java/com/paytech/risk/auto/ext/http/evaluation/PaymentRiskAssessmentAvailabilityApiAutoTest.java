package com.paytech.risk.auto.ext.http.evaluation;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.api.enums.PaymentRiskErrorCode.PAYMENT_RISK_NOT_CONFIGURED;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.risk.api.evaluation.PaymentRiskAssessmentApi;
import com.paytech.risk.api.evaluation.request.PaymentRiskAssessmentRequestBody;
import com.paytech.risk.control.core.enums.RiskListPurpose;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.PaymentRiskRequestBuilder;
import com.paytech.risk.test.checker.evaluation.PaymentRiskResponseChecker.Assessed;

/**
 * 支付尝试风控评估的可用性与输入边界
 * <li>无数据：本环境没有生效发布返回 503 PAYMENT_RISK_NOT_CONFIGURED，不写评估。</li>
 * <li>参数：字段缺失或格式不符返回 422；IP 不是合法字面量返回 400；二者都先于发布查询，不写评估。</li>
 * <li>技术失败：生效发布引用的变量损坏时返回 FAILED 并落库失败评估，不写累计事件；同一支付尝试重放返回原失败结果。</li>
 *
 * @see PaymentRiskAssessmentApi#assess(PaymentRiskAssessmentRequestBody)
 */
class PaymentRiskAssessmentAvailabilityApiAutoTest extends AbstractPaymentRiskApiTest {

    /** 没有生效发布：503，不写评估。 */
    @Test
    void test_noDataFound_notConfigured() {
        assess(PaymentRiskRequestBuilder.template().build()).assertProblem(503, PAYMENT_RISK_NOT_CONFIGURED.getCode());

        assertEquals(0, store.countPrimaryEvaluations());
    }

    /** 字段缺失或格式不符。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("contractViolations")
    void test_invalidParameter_contract(String scenario, UnaryOperator<PaymentRiskRequestBuilder> change) {
        assess(change.apply(PaymentRiskRequestBuilder.template()).build()).assertProblem(422,
            VALIDATION_ERROR.getCode());

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
            .rules(rule("THREE_DS_AMOUNT", "3DS amount", 10, action("ALLOW_PAYMENT_WITH_THREE_DS"),
                predicate("root", null, 1, "GT", variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money("500"))))
            .build());
        activeRelease(saveFlow(newCode("T_FLOW"), twoStageGraph(block, auth, reason)));
        store.markVariableDeleted(variable);
        PaymentRiskAssessmentRequestBody request = PaymentRiskRequestBuilder.template().build();

        Assessed failed = assess(request).assertResult(TechnicalStatus.FAILED.getCode(), null);

        Map<String, Object> evaluation = store.evaluation(failed.evaluationId());
        assertAll("失败评估", () -> assertEquals(EvaluationMode.PRIMARY.getCode(), evaluation.get("evaluation_mode")),
            () -> assertEquals(TechnicalStatus.FAILED.getCode(), evaluation.get("technical_status")),
            () -> assertEquals("EVALUATION_ERROR", evaluation.get("failure_code")),
            () -> assertNull(evaluation.get("business_decision")),
            () -> assertNull(
                store.assessment(Long.parseLong(request.getPaymentAttemptId())).get("authentication_requirement")),
            () -> assertEquals(List.of(), store.velocityEventCodes(failed.assessmentId())));
        assertEquals(failed, assess(request).assertResult(TechnicalStatus.FAILED.getCode(), null));
        assertEquals(1, store.countPrimaryEvaluations());
    }

    static Stream<Arguments> contractViolations() {
        return Stream.of(Arguments.of("缺少支付尝试", change(builder -> builder.paymentAttemptId(null))),
            Arguments.of("支付尝试非正整数", change(builder -> builder.paymentAttemptId("0"))),
            Arguments.of("风控 Action 非数字", change(builder -> builder.riskActionId("abc"))),
            Arguments.of("Operating Entity 小写", change(builder -> builder.entityNo("ae001"))),
            Arguments.of("金额为零", change(builder -> builder.amount("0.00"))),
            Arguments.of("金额超过 8 位小数", change(builder -> builder.amount("1.123456789"))),
            Arguments.of("币种小写", change(builder -> builder.currency("usd"))),
            Arguments.of("MCC 非 4 位", change(builder -> builder.mccCode("58161"))),
            Arguments.of("卡指纹过短", change(builder -> builder.cardFingerprint("short"))),
            Arguments.of("IP 超长", change(builder -> builder.clientIpAddress("1".repeat(46)))));
    }

    static Stream<String> invalidIps() {
        return Stream.of("not-an-ip", "999.1.1.1", "2001:db8::zz");
    }

    /** 参数化场景的请求改写。 */
    private static UnaryOperator<PaymentRiskRequestBuilder> change(UnaryOperator<PaymentRiskRequestBuilder> change) {
        return change;
    }
}

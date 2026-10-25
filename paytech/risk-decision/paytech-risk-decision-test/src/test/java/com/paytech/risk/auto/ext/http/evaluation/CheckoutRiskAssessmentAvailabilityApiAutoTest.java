package com.paytech.risk.auto.ext.http.evaluation;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.decision.api.enums.RiskDecisionEvaluationErrorCode.RISK_DECISION_NOT_CONFIGURED;
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

import com.paytech.risk.decision.api.evaluation.RiskDecisionApi;
import com.paytech.risk.decision.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.decision.service.evaluation.RuntimeEvaluationExecutor;
import com.paytech.risk.test.base.AbstractCheckoutRiskApiTest;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.CheckoutRiskRequestBuilder;
import com.paytech.risk.test.checker.evaluation.EvaluationResponseChecker.Assessed;
import com.paytech.risk.test.mapper.evaluation.CheckoutRiskTestMapper;

/**
 * 结账风控评估的可用性与输入边界
 * <li>无数据：本环境没有生效的结账执行发布返回 503 RISK_DECISION_NOT_CONFIGURED，不写评估与快照。</li>
 * <li>参数：业务身份或输入字段缺失、未知（含旧合同的 locale）、格式不符或标识超过 BIGINT 上限返回 422 并指出字段；旧合同 v4 未登记、经营主体来源与 Local Partner
 * 不一致返回 400；均先于发布查询，不写评估。</li>
 * <li>超时：执行耗时超过结账运行期点版本超时记为 TIMED_OUT 并落库，不写累计事件；重放返回原结果。</li>
 * <li>技术失败：生效发布引用的变量损坏时返回 FAILED 并落库失败评估与快照，不写累计事件；重放返回原失败结果。</li>
 *
 * @see RiskDecisionApi#evaluate(EvaluateDecisionRequestBody)
 */
class CheckoutRiskAssessmentAvailabilityApiAutoTest extends AbstractCheckoutRiskApiTest {
    /** 19 位但超过 BIGINT 上限的正整数。 */
    private static final String OVER_BIGINT = "9223372036854775808";

    /** 没有生效发布：503，不写评估与快照。 */
    @Test
    void test_noDataFound_notConfigured() {
        assess(CheckoutRiskRequestBuilder.template().build()).assertProblem(503, RISK_DECISION_NOT_CONFIGURED.getCode());

        assertEquals(0, store.countPrimaryEvaluations());
        assertEquals(0, store.countSnapshots());
    }

    /** 业务身份或输入字段缺失、未知或格式不符：422 并指出字段。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("contractViolations")
    void test_invalidParameter_contract(String scenario, String field, UnaryOperator<CheckoutRiskRequestBuilder> change) {
        assess(change.apply(CheckoutRiskRequestBuilder.template()).build()).assertProblem(422,
            VALIDATION_ERROR.getCode()).assertFieldError(field);

        assertEquals(0, store.countPrimaryEvaluations());
    }

    /** 旧合同版本未登记评估器；经营主体来源与 Local Partner 不一致：400，先于发布查询，不写评估。 */
    @Test
    void test_invalidParameter_semantic() {
        EvaluateDecisionRequestBody legacy = CheckoutRiskRequestBuilder.template().build();
        legacy.setContractVersion(DecisionPointAuthoringDescriptor.legacyCheckoutPrepare().point().inputContractVersion());

        assess(legacy).assertProblem(400, INVALID_REQUEST.getCode());
        assess(CheckoutRiskRequestBuilder.template().partner(null).build()).assertProblem(400, INVALID_REQUEST.getCode());
        assess(CheckoutRiskRequestBuilder.template().localPartnerId("300001").build()).assertProblem(400,
            INVALID_REQUEST.getCode());

        assertEquals(0, store.countPrimaryEvaluations());
        assertEquals(0, store.countSnapshots());
    }

    /** 执行超过结账运行期点版本超时：TIMED_OUT 落库，不形成业务决定，快照照常冻结；重放返回原结果。 */
    @Test
    void test_businessRuleViolation_timedOut() {
        activeRelease(saveFlow(checkoutCode("FLOW"), blockGraph(largeAmountRuleSet(null), rejectionReason())));
        checkouts.mockEvaluationTimeout();
        EvaluateDecisionRequestBody request = CheckoutRiskRequestBuilder.template().build();

        Assessed timedOut = assess(request).assertResult(TechnicalStatus.TIMED_OUT.getCode());

        Map<String, Object> evaluation = store.evaluation(timedOut.evaluationId());
        assertAll("超时评估", () -> assertEquals(TechnicalStatus.TIMED_OUT.getCode(), evaluation.get("technical_status")),
            () -> assertEquals(RuntimeEvaluationExecutor.EVALUATION_TIMEOUT, evaluation.get("failure_code")),
            () -> assertNull(evaluation.get("business_decision")),
            () -> assertNotNull(store.snapshot(Long.parseLong(request.getBusinessKey())), "快照照常冻结"),
            () -> assertEquals(List.of("start", "block", "allow"), store.flowNodeKeys(timedOut.evaluationId()),
                "超时保留流程轨迹"));
        assertEquals(timedOut, assess(request).assertResult(TechnicalStatus.TIMED_OUT.getCode()));
        assertEquals(1, store.countPrimaryEvaluations());
        assertEquals(0, store.countVelocityEvents(CheckoutRiskTestMapper.SOURCE_TYPE), "超时不写累计事件");
    }

    /** 生效发布引用的常量变量被删除：FAILED 落库，没有轨迹；重放返回原失败结果。 */
    @Test
    void test_businessRuleViolation_technicalFailure() {
        String limit = createScalarConstant("LIMIT", "50");
        activeRelease(saveFlow(checkoutCode("FLOW"), blockGraph(largeAmountRuleSet(limit), rejectionReason())));
        checkouts.mockVariableDeleted(limit);
        EvaluateDecisionRequestBody request = CheckoutRiskRequestBuilder.template().build();

        Assessed failed = assess(request).assertResult(TechnicalStatus.FAILED.getCode());

        Map<String, Object> evaluation = store.evaluation(failed.evaluationId());
        assertAll("失败评估", () -> assertEquals(EvaluationMode.PRIMARY.getCode(), evaluation.get("evaluation_mode")),
            () -> assertEquals(TechnicalStatus.FAILED.getCode(), evaluation.get("technical_status")),
            () -> assertEquals(RuntimeEvaluationExecutor.EVALUATION_ERROR, evaluation.get("failure_code")),
            () -> assertNull(evaluation.get("business_decision")),
            () -> assertNotNull(store.snapshot(Long.parseLong(request.getBusinessKey())), "快照照常冻结"),
            () -> assertEquals(List.of(), store.flowNodeKeys(failed.evaluationId()), "执行内容不可读，没有流程轨迹"));
        assertEquals(failed, assess(request).assertResult(TechnicalStatus.FAILED.getCode()));
        assertEquals(1, store.countPrimaryEvaluations());
        assertEquals(0, store.countVelocityEvents(CheckoutRiskTestMapper.SOURCE_TYPE), "失败不写累计事件");
    }

    static Stream<Arguments> contractViolations() {
        return Stream.of(Arguments.of("缺少订单 ID", "businessKey", change(builder -> builder.orderId(null))),
            Arguments.of("订单 ID 非正整数", "businessKey", change(builder -> builder.orderId("0"))),
            Arguments.of("订单 ID 超过 BIGINT 上限", "businessKey", change(builder -> builder.orderId(OVER_BIGINT))),
            Arguments.of("Client ID 超过 BIGINT 上限", "inputs.clientId", change(builder -> builder.input("clientId",
                OVER_BIGINT))),
            Arguments.of("Local Partner 超过 BIGINT 上限", "inputs.localPartnerId",
                change(builder -> builder.partner(OVER_BIGINT))),
            Arguments.of("缺少净额", "inputs.netAmount", change(builder -> builder.netAmount(null))),
            Arguments.of("缺少明细行数", "inputs.lineCount", change(builder -> builder.lineCount(null))),
            Arguments.of("旧合同字段 locale", "inputs.locale", change(builder -> builder.input("locale", "en-AE"))),
            Arguments.of("Operating Entity 小写", "inputs.entityNo", change(builder -> builder.entityNo("ae001"))),
            Arguments.of("经营主体来源未登记", "inputs.entitySourceType",
                change(builder -> builder.entitySourceType("OTHER"))),
            Arguments.of("经营主体来源 PLATFORM 已删除", "inputs.entitySourceType",
                change(builder -> builder.entitySourceType(CheckoutRiskRequestBuilder.RETIRED_PLATFORM_SOURCE))),
            Arguments.of("Local Partner 非数字", "inputs.localPartnerId",
                change(builder -> builder.partner("abc"))),
            Arguments.of("国家小写", "inputs.consumerCountryCode", change(builder -> builder.consumerCountryCode("ae"))),
            Arguments.of("币种小写", "inputs.currency", change(builder -> builder.currency("usd"))),
            Arguments.of("金额超过 8 位小数", "inputs.netAmount", change(builder -> builder.netAmount("1.123456789"))),
            Arguments.of("金额为负", "inputs.discountAmount", change(builder -> builder.input("discountAmount", "-1"))),
            Arguments.of("MCC 非 4 位", "inputs.mccCode", change(builder -> builder.mccCode("58161"))),
            Arguments.of("明细行数为零", "inputs.lineCount", change(builder -> builder.lineCount("0"))),
            Arguments.of("明细行数超过 3 位", "inputs.lineCount", change(builder -> builder.lineCount("1000"))));
    }

    /**
     * 审核规则集：未给常量时净额超过 1000 审核；给出普通数值常量时明细行数超过该常量审核
     *
     * @param scalarLimit 普通数值常量变量编码；为空时比较净额与金额字面量
     * @return 冻结的规则集版本 ID
     */
    private long largeAmountRuleSet(String scalarLimit) {
        var condition = scalarLimit == null
            ? predicate("root", null, 1, "GT", variableOperand("INPUT_NET_AMOUNT", "NUMBER"), money("1000"))
            : predicate("root", null, 1, "GT", variableOperand("INPUT_LINE_COUNT", "NUMBER"),
                variable("RIGHT", 1, scalarLimit, "NUMBER"));
        return frozenRuleSet(RuleSetCreateRequestBuilder.template(checkoutCode("BLOCK")).pointCode(CHECKOUT)
            .rules(rule("LARGE", "LARGE", 10, action(REVIEW), condition)).build());
    }

    /** 参数化场景的请求改写。 */
    private static UnaryOperator<CheckoutRiskRequestBuilder> change(UnaryOperator<CheckoutRiskRequestBuilder> change) {
        return change;
    }
}

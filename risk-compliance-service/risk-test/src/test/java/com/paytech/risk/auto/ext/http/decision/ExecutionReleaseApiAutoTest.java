package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.BUSINESS_CHECK_FAILED;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.action;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.predicate;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.rule;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.api.decision.ExecutionReleaseManagementApi;
import com.paytech.risk.api.decision.request.CreateExecutionReleaseRequestBody;
import com.paytech.risk.api.decision.request.StartShadowRunRequestBody;
import com.paytech.risk.control.core.enums.RiskListPurpose;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.decision.domain.enums.ShadowRunStatus;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.PaymentRiskRequestBuilder;
import com.paytech.risk.test.checker.decision.ExecutionReleaseResponseChecker;

/**
 * 支付决策点执行发布：创建、静态校验、影子运行与激活
 * <li>正常流程：首个发布校验通过后由一人直接激活；已有生效发布时，候选须完成影子回放（最近的正式评估）且技术门槛通过才能激活，原发布被替代，新评估按候选执行。</li>
 * <li>业务规则：未校验的发布不能影子运行或激活；无生效发布时不能影子运行；未完成影子运行不能替代生效发布；引用损坏的发布八项检查全部失败、被拒绝且不能激活。</li>
 * <li>参数：决策点不支持执行发布、流程版本不存在返回 400；字段缺失或越界返回 422；发布 ID 非法返回 400。</li>
 * <li>无数据：发布不存在返回 404。</li>
 *
 * @see ExecutionReleaseManagementApi
 */
class ExecutionReleaseApiAutoTest extends AbstractPaymentRiskApiTest {
    /** 等待影子回放完成的上限。 */
    private static final long SHADOW_WAIT_MILLIS = 15_000;

    private static final String POINT = PAYMENT;

    private static final String TEST = DecisionEnvironment.TEST.getCode();

    private String reason;

    private long blockRuleSet;

    /** 每例准备一个金额审核拦截规则集。 */
    @BeforeEach
    void setUpClassMocks() {
        reason = rejectionReason();
        blockRuleSet = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_BLOCK")).pointCode(POINT)
            .rules(rule("LARGE_AMOUNT", "Large amount", 10, action("REVIEW_PAYMENT"), predicate("root", null, 1, "GT",
                variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money("1000"))))
            .build());
    }

    /** 首个发布：DRAFT → 校验通过 SHADOWING → 一人激活 ACTIVE，并记录状态审计；评估按该发布执行。 */
    @Test
    void test_normalFlow_firstActivation() {
        long flow = saveFlow(newCode("T_FLOW"), twoStageGraph(blockRuleSet, authRuleSet("500"), reason));

        long releaseId = create(flow).assertRelease(DecisionReleaseStatus.DRAFT.getCode(), POINT, TEST);
        command(releaseId, "validate").assertRelease(DecisionReleaseStatus.SHADOWING.getCode(), POINT, TEST);
        command(releaseId, "activate").assertRelease(DecisionReleaseStatus.ACTIVE.getCode(), POINT, TEST);

        get(releaseId).assertChecks(List.of()).assertTimes(true, false)
            .assertRelease(DecisionReleaseStatus.ACTIVE.getCode(), POINT, TEST);
        List<Map<String, Object>> audits = store.releaseAudits(releaseId);
        assertFalse(audits.isEmpty(), "状态审计");
        assertEquals(ACTOR_ID, ((Number)store.release(releaseId).get("created_by")).longValue());
        assess(PaymentRiskRequestBuilder.template().amount("600").build()).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.THREE_DS_REQUIRED.getCode());
    }

    /** 已有生效发布：候选影子回放最近正式评估后激活，原发布被替代；影子样本不写交易评估，新评估按候选执行。 */
    @Test
    void test_normalFlow_shadowAndSupersede() {
        long baseline = activeRelease(saveFlow(newCode("T_FLOW"), twoStageGraph(blockRuleSet, authRuleSet("500"), reason)));
        for (String amount : List.of("100", "600", "1500")) {
            assess(PaymentRiskRequestBuilder.template().amount(amount).build());
        }
        long candidate = validatedRelease(saveFlow(newCode("T_FLOW"),
            twoStageGraph(blockRuleSet, authRuleSet("50"), reason)));

        command(candidate, "activate").assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
        shadow(candidate, 10).assertRelease(DecisionReleaseStatus.SHADOWING.getCode(), POINT, TEST);
        long shadowRunId = awaitShadowRun(candidate).assertShadowRun(baseline, 3, StaticCheckStatus.PASSED.getCode());
        command(candidate, "activate").assertRelease(DecisionReleaseStatus.ACTIVE.getCode(), POINT, TEST);

        get(baseline).assertTimes(true, true).assertRelease(DecisionReleaseStatus.SUPERSEDED.getCode(), POINT, TEST);
        List<Map<String, Object>> samples = store.shadowSamples(shadowRunId);
        assertAll("影子样本",
            () -> assertEquals(3, samples.size()),
            () -> assertEquals(List.of(EvaluationMode.SHADOW.getCode()),
                samples.stream().map(row -> row.get("evaluation_mode")).distinct().toList()),
            () -> assertEquals(3, store.countPrimaryEvaluations(), "影子不新增正式评估"));
        assess(PaymentRiskRequestBuilder.template().amount("100").build()).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.THREE_DS_REQUIRED.getCode());
    }

    /** 重复校验返回当前结果，不重新检查。 */
    @Test
    void test_edgeCase_validateTwice() {
        long releaseId = validatedRelease(saveFlow(newCode("T_FLOW"),
            twoStageGraph(blockRuleSet, authRuleSet("500"), reason)));
        int audits = store.releaseAudits(releaseId).size();

        command(releaseId, "validate").assertChecks(List.of())
            .assertRelease(DecisionReleaseStatus.SHADOWING.getCode(), POINT, TEST);

        assertEquals(audits, store.releaseAudits(releaseId).size());
    }

    /** 未校验的发布不能影子运行或激活；无生效发布时影子运行无基线。 */
    @Test
    void test_businessRuleViolation_order() {
        long flow = saveFlow(newCode("T_FLOW"), twoStageGraph(blockRuleSet, authRuleSet("500"), reason));
        long draft = create(flow).assertRelease(DecisionReleaseStatus.DRAFT.getCode(), POINT, TEST);
        get(draft).assertNoChecks();

        shadow(draft, 10).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
        command(draft, "activate").assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
        command(draft, "validate").assertRelease(DecisionReleaseStatus.SHADOWING.getCode(), POINT, TEST);
        shadow(draft, 10).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
    }

    /** 发布引用的名单变量在校验前被删除：内容无法读取，八项检查全部失败，发布被拒绝且不能激活。 */
    @Test
    void test_businessRuleViolation_rejected() {
        String list = newCode("T_LIST");
        createList(list, RiskSubjectType.IP_ADDRESS.getCode(), RiskListPurpose.BLOCK.getCode());
        String variable = newCode("T_IP_BLOCKED");
        createListVariable(variable, list, RiskSubjectType.IP_ADDRESS.getCode());
        long block = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_BLOCK")).pointCode(POINT)
            .rules(rule("BLOCKED_IP", "BLOCKED_IP", 10, reason, "支付被风控拒绝", action("DENY_PAYMENT"), List.of(),
                predicate("root", null, 1, "EQ", variableOperand(variable, "BOOLEAN"), bool(true))))
            .build());
        long releaseId = create(saveFlow(newCode("T_FLOW"), twoStageGraph(block, authRuleSet("500"), reason)))
            .assertRelease(DecisionReleaseStatus.DRAFT.getCode(), POINT, TEST);
        store.markVariableDeleted(variable);

        command(releaseId, "validate").assertChecks(List.of(StaticCheckCode.values()))
            .assertRelease(DecisionReleaseStatus.REJECTED.getCode(), POINT, TEST);

        command(releaseId, "activate").assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
    }

    /** 决策点不支持执行发布、流程版本不存在或属于其他决策点。 */
    @Test
    void test_invalidParameter() {
        long flow = saveFlow(newCode("T_FLOW"), twoStageGraph(blockRuleSet, authRuleSet("500"), reason));

        post(EXECUTIONS, createBody("CHECKOUT_PREPARE", flow)).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
        post(EXECUTIONS, createBody(POINT, 9876543210L)).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
        get("abc").assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 请求体缺字段或影子样本数越界。 */
    @Test
    void test_invalidParameter_contract() {
        long releaseId = validatedRelease(saveFlow(newCode("T_FLOW"),
            twoStageGraph(blockRuleSet, authRuleSet("500"), reason)));

        post(EXECUTIONS, new CreateExecutionReleaseRequestBody()).assertProblem(422, VALIDATION_ERROR.getCode());
        shadow(releaseId, 0).assertProblem(422, VALIDATION_ERROR.getCode());
        shadow(releaseId, 2001).assertProblem(422, VALIDATION_ERROR.getCode());
    }

    /** 发布不存在。 */
    @Test
    void test_noDataFound() {
        long missing = 9876543210L;

        get(missing).assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
        command(missing, "validate").assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
        command(missing, "activate").assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
        shadow(missing, 10).assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }

    /** 金额超过阈值时要求 3DS 的认证规则集。 */
    private long authRuleSet(String threshold) {
        return frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_AUTH")).pointCode(POINT)
            .rules(rule("THREE_DS_AMOUNT", "3DS amount", 10, action("ALLOW_PAYMENT_WITH_THREE_DS"),
                predicate("root", null, 1, "GT", variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money(threshold))))
            .build());
    }

    /** 轮询发布直到影子批次结束。 */
    private ExecutionReleaseResponseChecker awaitShadowRun(long releaseId) {
        long deadline = System.currentTimeMillis() + SHADOW_WAIT_MILLIS;
        while (true) {
            ExecutionReleaseResponseChecker current = get(releaseId);
            if (!ShadowRunStatus.RUNNING.getCode().equals(current.shadowRunStatus())) {
                return current;
            }
            if (System.currentTimeMillis() > deadline) {
                fail("影子回放未在期限内完成: " + releaseId);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("等待影子回放被中断");
            }
        }
    }

    /** 创建执行发布。 */
    private ExecutionReleaseResponseChecker create(long flowVersionId) {
        return post(EXECUTIONS, createBody(POINT, flowVersionId));
    }

    /** 校验或激活。 */
    private ExecutionReleaseResponseChecker command(long releaseId, String command) {
        return post(EXECUTIONS + "/" + releaseId + "/" + command, "{}");
    }

    /** 启动影子运行。 */
    private ExecutionReleaseResponseChecker shadow(long releaseId, int sampleTarget) {
        StartShadowRunRequestBody body = new StartShadowRunRequestBody();
        body.setSampleTarget(sampleTarget);
        return post(EXECUTIONS + "/" + releaseId + "/shadow-runs", body);
    }

    /** 查询发布。 */
    private ExecutionReleaseResponseChecker get(Object releaseId) {
        HttpHeaders headers = headers();
        return new ExecutionReleaseResponseChecker(http.get(EXECUTIONS + "/" + releaseId, headers), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    /** 带可信操作者的 POST。 */
    private ExecutionReleaseResponseChecker post(String path, Object body) {
        HttpHeaders headers = headers();
        return new ExecutionReleaseResponseChecker(http.post(path, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    /** 创建请求。 */
    private static CreateExecutionReleaseRequestBody createBody(String pointCode, long flowVersionId) {
        CreateExecutionReleaseRequestBody body = new CreateExecutionReleaseRequestBody();
        body.setPointCode(pointCode);
        body.setFlowVersionId(Long.toString(flowVersionId));
        return body;
    }
}

package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.BUSINESS_CHECK_FAILED;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_RULE_SET_IN_USE;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.action;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.predicate;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.rule;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleRequestBody;
import com.paytech.risk.api.decision.ExecutionReleaseManagementApi;
import com.paytech.risk.api.decision.request.CreateExecutionReleaseRequestBody;
import com.paytech.risk.api.decision.request.CreateVariableRequestBody;
import com.paytech.risk.api.decision.request.StartShadowRunRequestBody;
import com.paytech.risk.control.core.enums.RiskListPurpose;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.decision.domain.enums.RuleTraceType;
import com.paytech.risk.decision.domain.enums.ShadowRunStatus;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.PaymentRiskRequestBuilder;
import com.paytech.risk.test.checker.decision.ExecutionReleaseResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 支付决策点执行发布：创建、静态校验、影子运行与激活
 * <li>正常流程：首个发布校验通过后由一人直接激活；已有生效发布时，候选须完成影子回放（最近的正式评估）且技术门槛通过才能激活，原发布被替代，新评估按候选执行。</li>
 * <li>业务规则：生效中或影子中的执行发布经决策流引用的规则集不能删除，发布被替代后可删除；未校验的发布不能影子运行或激活；无生效发布时不能影子运行；未完成影子运行不能替代生效发布；引用损坏的发布八项检查全部失败、被拒绝且不能激活。</li>
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

    /** 已有生效发布：候选影子回放最近正式评估后激活，原发布被替代；影子样本不写交易评估但写规则与流程轨迹，新评估按候选执行。 */
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
            () -> assertEquals(3, store.countPrimaryEvaluations(), "影子不新增正式评估"),
            () -> assertTrue(samples.stream().allMatch(row -> !store.flowTraces(id(row)).isEmpty()), "影子流程轨迹"),
            () -> assertTrue(samples.stream().allMatch(row -> !store.traces(id(row), RuleTraceType.RULE.getCode()).isEmpty()),
                "影子规则轨迹"));
        assess(PaymentRiskRequestBuilder.template().amount("100").build()).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.THREE_DS_REQUIRED.getCode());
    }

    /** 影子汇总按结论变化分类：放行转审核、放行转拒绝、拒绝转放行各一；审核转放行不计入拒绝转放行。 */
    @Test
    void test_normalFlow_shadowDecisionChanges() {
        long baselineBlock = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_BLOCK")).pointCode(POINT)
            .rules(amountRule("HUGE_AMOUNT", 10, "DENY_PAYMENT", "GT", "2000"),
                amountRule("LARGE_AMOUNT", 20, "REVIEW_PAYMENT", "GT", "1000"))
            .build());
        long baseline = activeRelease(saveFlow(newCode("T_FLOW"),
            twoStageGraph(baselineBlock, authRuleSet("500"), reason)));
        for (String amount : List.of("100", "600", "1500", "2500")) {
            assess(PaymentRiskRequestBuilder.template().amount(amount).build());
        }
        long candidateBlock = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_BLOCK")).pointCode(POINT)
            .rules(amountRule("SMALL_AMOUNT", 10, "REVIEW_PAYMENT", "LT", "200"),
                amountRule("MID_AMOUNT", 20, "DENY_PAYMENT", "LT", "1000"))
            .build());
        long candidate = validatedRelease(saveFlow(newCode("T_FLOW"),
            twoStageGraph(candidateBlock, authRuleSet("100000"), reason)));

        shadow(candidate, 10);

        awaitShadowRun(candidate).assertDecisionChanges(1, 1, 1).assertShadowRun(baseline, 4,
            StaticCheckStatus.PASSED.getCode());
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

    /** 流程引用尚未冻结的规则集版本：校验只拒绝引用完整性一项。 */
    @Test
    void test_businessRuleViolation_draftRuleSet() {
        String draft = postOk("/decision-rule-sets/v1/rule-sets", RuleSetCreateRequestBuilder.template(newCode("T_DRAFT"))
            .pointCode(POINT).rules(amountRule("LARGE_AMOUNT", 10, "REVIEW_PAYMENT", "GT", "1000")).build())
            .path("ruleSetVersionId").asString();
        long releaseId = create(saveFlow(newCode("T_FLOW"),
            twoStageGraph(Long.parseLong(draft), authRuleSet("500"), reason)))
            .assertRelease(DecisionReleaseStatus.DRAFT.getCode(), POINT, TEST);

        command(releaseId, "validate").assertChecks(List.of(StaticCheckCode.REFERENCE_INTEGRITY))
            .assertRelease(DecisionReleaseStatus.REJECTED.getCode(), POINT, TEST);
    }

    /** 影子运行时候选内容已无法读取：批次保持运行中并登记可重试失败，候选不能激活。 */
    @Test
    void test_businessRuleViolation_shadowUnreadable() {
        activeRelease(saveFlow(newCode("T_FLOW"), twoStageGraph(blockRuleSet, authRuleSet("500"), reason)));
        assess(PaymentRiskRequestBuilder.template().build());
        String constant = newCode("T_LIMIT");
        CreateVariableRequestBody variable = featureVariable(constant, "CONSTANT", "NUMBER");
        variable.setNumericUnit("SCALAR");
        variable.setCurrentValue("1000");
        postOk("/decision-variables/v1/variables", variable);
        long block = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_BLOCK")).pointCode(POINT)
            .rules(rule("OVER_LIMIT", "OVER_LIMIT", 10, action("REVIEW_PAYMENT"), predicate("root", null, 1, "GT",
                variableOperand(constant, "NUMBER"), scalar("2000"))))
            .build());
        long candidate = validatedRelease(saveFlow(newCode("T_FLOW"), twoStageGraph(block, authRuleSet("500"), reason)));
        store.markVariableDeleted(constant);

        long shadowRunId = Long.parseLong(shadow(candidate, 10).shadowRunId());

        awaitRetryError(shadowRunId);
        assertEquals(ShadowRunStatus.RUNNING.getCode(), get(candidate).shadowRunStatus());
        command(candidate, "activate").assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
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

    /** 金额与阈值比较命中即给出终态动作的规则；拒绝动作附原因编码。 */
    private RuleRequestBody amountRule(String code, int priority, String actionCode, String operator, String amount) {
        boolean deny = "DENY_PAYMENT".equals(actionCode);
        return rule(code, code, priority, deny ? reason : null, deny ? "支付被风控拒绝" : null, action(actionCode),
            List.of(), predicate("root", null, 1, operator, variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money(amount)));
    }

    /** 规则集被生效中或影子中的执行发布引用时返回 409 IN_USE；原发布被替代后，其独有的规则集可以删除。 */
    @Test
    void test_businessRuleViolation_ruleSetInUse() {
        long baselineAuth = authRuleSet("500");
        long baseline = activeRelease(saveFlow(newCode("T_FLOW"), twoStageGraph(blockRuleSet, baselineAuth, reason)));
        assess(PaymentRiskRequestBuilder.template().amount("600").build());
        long candidateAuth = authRuleSet("50");
        long candidate = validatedRelease(saveFlow(newCode("T_FLOW"),
            twoStageGraph(blockRuleSet, candidateAuth, reason)));

        assertDeleteRejected(baselineAuth);
        assertDeleteRejected(candidateAuth);
        assertDeleteRejected(blockRuleSet);
        assertManagementQueries(baselineAuth, true);

        shadow(candidate, 10).assertRelease(DecisionReleaseStatus.SHADOWING.getCode(), POINT, TEST);
        awaitShadowRun(candidate).assertShadowRun(baseline, 1, StaticCheckStatus.PASSED.getCode());
        command(candidate, "activate").assertRelease(DecisionReleaseStatus.ACTIVE.getCode(), POINT, TEST);

        assertManagementQueries(baselineAuth, false);
        assertEquals(200, deleteRuleSet(baselineAuth).getStatus().value(), "被替代发布独有的规则集可删除");
        assertDeleteRejected(candidateAuth);
        assertDeleteRejected(blockRuleSet);
    }

    /** 存在执行发布时规则集列表、版本历史与发布引用查询正常返回，列表按执行发布引用标记是否在用。 */
    private void assertManagementQueries(long ruleSetVersionId, boolean inUse) {
        Map<String, Object> root = store.ruleSetRoot(ruleSetVersionId);
        String rules = "/decision-rule-sets/v1/rule-sets";
        JsonNode list = getOk(rules + "?pointCode=" + POINT + "&ruleSetCode=" + root.get("rule_set_code"));
        assertEquals(inUse, list.path("items").path(0).path("inUse").asBoolean(), "列表在用标记");
        getOk(rules + "/" + root.get("rule_set_id") + "/versions?maxVersionNo=1");
        getOk(rules + "/" + root.get("rule_set_id") + "/release-references");
    }

    /** GET 并要求 200。 */
    private JsonNode getOk(String path) {
        var result = http.get(path, headers());
        assertEquals(200, result.getStatus().value(), path);
        return objectMapper.readTree(result.getResponseBody());
    }

    /** 删除规则集被拒绝为正在使用，管理根不变。 */
    private void assertDeleteRejected(long ruleSetVersionId) {
        Map<String, Object> before = store.ruleSetRoot(ruleSetVersionId);
        EntityExchangeResult<byte[]> result = deleteRuleSet(ruleSetVersionId);

        assertEquals(409, result.getStatus().value(), "删除在用规则集");
        assertEquals(RISK_RULE_SET_IN_USE.getCode(), objectMapper.readTree(result.getResponseBody()).path("code").asString());
        assertEquals(before, store.ruleSetRoot(ruleSetVersionId));
    }

    /** 按当前管理版本整体删除规则集版本所属的规则集。 */
    private EntityExchangeResult<byte[]> deleteRuleSet(long ruleSetVersionId) {
        Map<String, Object> root = store.ruleSetRoot(ruleSetVersionId);
        HttpHeaders headers = headers();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(HttpMethod.DELETE, "/decision-rule-sets/v1/rule-sets/" + root.get("rule_set_id"), headers,
            ("{\"expectedVersion\":\"" + root.get("version") + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    /** 金额超过阈值时要求 3DS 的认证规则集。 */
    private long authRuleSet(String threshold) {
        return frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_AUTH")).pointCode(POINT)
            .rules(rule("THREE_DS_AMOUNT", "3DS amount", 10, action("ALLOW_PAYMENT"),
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

    /** 等待影子批次补偿任务登记一次可重试失败。 */
    private void awaitRetryError(long shadowRunId) {
        long deadline = System.currentTimeMillis() + SHADOW_WAIT_MILLIS;
        while (store.shadowRunRetryError(shadowRunId) == null) {
            if (System.currentTimeMillis() > deadline) {
                fail("影子批次未登记可重试失败: " + shadowRunId);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("等待影子批次失败被中断");
            }
        }
        assertEquals("SHADOW_RUN_INCOMPLETE", store.shadowRunRetryError(shadowRunId));
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

    /** 评估行的 ID。 */
    private static long id(Map<String, Object> evaluation) {
        return ((Number)evaluation.get("evaluation_id")).longValue();
    }
}

package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.BUSINESS_CHECK_FAILED;
import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_RULE_SET_IN_USE;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.decision.api.ExecutionReleaseManagementApi;
import com.paytech.risk.decision.api.request.CreateExecutionReleaseRequestBody;
import com.paytech.risk.decision.api.request.StartShadowRunRequestBody;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.decision.domain.enums.ShadowRunStatus;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domain.enums.VelocityMetric;
import com.paytech.risk.test.base.AbstractCheckoutRiskApiTest;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.CheckoutRiskRequestBuilder;
import com.paytech.risk.test.checker.decision.ExecutionReleaseResponseChecker;
import com.paytech.risk.test.mapper.decision.FlowTestMapper;
import com.paytech.risk.test.mapper.evaluation.CheckoutRiskTestMapper;

/**
 * 结账决策点执行发布：创建、静态校验、影子运行与激活
 * <li>正常流程：首个发布引用结账运行期冻结点版本（220001，内容摘要与工程合同一致）；校验通过后激活，结账评估按该发布执行。</li>
 * <li>影子：已有生效发布时，候选回放最近的结账正式评估；结账没有附加输出，“输出变化”恒为否；激活后原发布被替代。</li>
 * <li>影子含实时累计：样本按原评估时间计算累计值，回放不写累计事件。</li>
 * <li>业务规则：决策流版本使用旧输入合同（v4）不能创建执行发布；生效发布经决策流引用的结账规则集不能删除。</li>
 *
 * @see ExecutionReleaseManagementApi
 */
class CheckoutExecutionReleaseApiAutoTest extends AbstractCheckoutRiskApiTest {
    /** 等待影子回放完成的上限。 */
    private static final long SHADOW_WAIT_MILLIS = 15_000;

    private static final String TEST = DecisionEnvironment.TEST.getCode();

    @Autowired
    private FlowTestMapper flowTestMapper;

    private String reason;

    /** 每例准备一个拒绝原因。 */
    @BeforeEach
    void setUpClassMocks() {
        reason = rejectionReason();
    }

    /** 首个发布：DRAFT 引用 220001 → 校验通过 SHADOWING → 激活 ACTIVE；结账评估按该发布执行。 */
    @Test
    void test_normalFlow_firstActivation() {
        long flow = saveFlow(checkoutCode("FLOW"), blockGraph(netAmountRuleSet("1000"), reason));

        long releaseId = create(flow).assertRelease(DecisionReleaseStatus.DRAFT.getCode(), CHECKOUT, TEST);
        command(releaseId, "validate").assertChecks(List.of())
            .assertRelease(DecisionReleaseStatus.SHADOWING.getCode(), CHECKOUT, TEST);
        command(releaseId, "activate").assertRelease(DecisionReleaseStatus.ACTIVE.getCode(), CHECKOUT, TEST);

        assertEquals(CheckoutRiskTestMapper.RUNTIME_POINT_VERSION_ID,
            ((Number)store.release(releaseId).get("point_version_id")).longValue(), "结账运行期点版本");
        assess(CheckoutRiskRequestBuilder.template().amounts("1500", "0", "1500").build())
            .assertResult(DecisionValue.REVIEW.getCode());
        assess(CheckoutRiskRequestBuilder.template().build()).assertResult(DecisionValue.ALLOW.getCode());
    }

    /** 已有生效发布：候选回放最近的结账正式评估，结论变化按样本统计、输出变化恒为否；激活后原发布被替代。 */
    @Test
    void test_normalFlow_shadowAndSupersede() {
        long baseline = activeRelease(saveFlow(checkoutCode("FLOW"), blockGraph(netAmountRuleSet("1000"), reason)));
        for (String net : List.of("100", "500", "1500")) {
            assess(CheckoutRiskRequestBuilder.template().amounts(net, "0", net).build());
        }
        long candidate = validatedRelease(saveFlow(checkoutCode("FLOW"), blockGraph(netAmountRuleSet("300"), reason)));

        shadow(candidate, 10).assertRelease(DecisionReleaseStatus.SHADOWING.getCode(), CHECKOUT, TEST);
        long shadowRunId = awaitShadowRun(candidate).assertDecisionChanges(1, 0, 0)
            .assertShadowRun(baseline, 3, StaticCheckStatus.PASSED.getCode());
        command(candidate, "activate").assertRelease(DecisionReleaseStatus.ACTIVE.getCode(), CHECKOUT, TEST);

        List<Map<String, Object>> samples = store.shadowSamples(shadowRunId);
        assertAll("影子样本", () -> assertEquals(3, samples.size()),
            () -> assertEquals(List.of(EvaluationMode.SHADOW.getCode()),
                samples.stream().map(row -> row.get("evaluation_mode")).distinct().toList()),
            () -> assertTrue(samples.stream().noneMatch(row -> Boolean.TRUE.equals(row.get("action_changed_flag"))),
                "结账没有附加输出"),
            () -> assertEquals(3, store.countPrimaryEvaluations(), "影子不新增正式评估"));
        get(baseline).assertTimes(true, true).assertRelease(DecisionReleaseStatus.SUPERSEDED.getCode(), CHECKOUT, TEST);
        assess(CheckoutRiskRequestBuilder.template().amounts("500", "0", "500").build())
            .assertResult(DecisionValue.REVIEW.getCode());
    }

    /** 含 Client 1 小时评估次数的发布：三次评估依次累计 0、1、2；阈值从 10 改为 2 的候选只改变第三个样本，回放不写累计事件。 */
    @Test
    void test_normalFlow_velocityShadow() {
        String count = checkoutCode("CLIENT_COUNT");
        createVelocityVariable(count, VelocityEventCode.CHECKOUT_ASSESSED.getCode(), RiskSubjectType.CLIENT.getCode(),
            VelocityMetric.COUNT.getCode(), null, 3600, false);
        long baseline = activeRelease(saveFlow(checkoutCode("FLOW"), blockGraph(clientCountRuleSet(count, "10"), reason)));
        for (int i = 0; i < 3; i++) {
            assess(CheckoutRiskRequestBuilder.template().build()).assertResult(DecisionValue.ALLOW.getCode());
        }
        long candidate = validatedRelease(saveFlow(checkoutCode("FLOW"), blockGraph(clientCountRuleSet(count, "2"), reason)));

        shadow(candidate, 10).assertRelease(DecisionReleaseStatus.SHADOWING.getCode(), CHECKOUT, TEST);
        awaitShadowRun(candidate).assertDecisionChanges(1, 0, 0)
            .assertShadowRun(baseline, 3, StaticCheckStatus.PASSED.getCode());

        assertEquals(6, store.countVelocityEvents(CheckoutRiskTestMapper.SOURCE_TYPE), "三次放行各写评估与放行两条，影子不写累计事件");
    }

    /** 决策流版本使用旧输入合同：创建执行发布被拒绝，须重新保存后发布。 */
    @Test
    void test_businessRuleViolation_legacyContractFlow() {
        long flow = saveFlow(checkoutCode("FLOW"), blockGraph(netAmountRuleSet("1000"), reason));
        flowTestMapper.updateVersionContract(flow, RuleSetBuilder.LEGACY_CONTRACT_VERSION);

        create(flow).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());

        assertEquals(0, store.countPrimaryEvaluations());
    }

    /** 生效中的结账执行发布经决策流引用的规则集不能删除，管理根不变。 */
    @Test
    void test_businessRuleViolation_ruleSetInUse() {
        long ruleSet = netAmountRuleSet("1000");
        activeRelease(saveFlow(checkoutCode("FLOW"), blockGraph(ruleSet, reason)));
        Map<String, Object> before = store.ruleSetRoot(ruleSet);

        EntityExchangeResult<byte[]> result = deleteRuleSet(before);

        assertEquals(409, result.getStatus().value(), "删除在用规则集");
        assertEquals(RISK_RULE_SET_IN_USE.getCode(), objectMapper.readTree(result.getResponseBody()).path("code").asString());
        assertEquals(before, store.ruleSetRoot(ruleSet));
    }

    /** 净额超过阈值审核的冻结结账规则集。 */
    private long netAmountRuleSet(String threshold) {
        return frozenRuleSet(RuleSetCreateRequestBuilder.template(checkoutCode("BLOCK")).pointCode(CHECKOUT)
            .rules(rule("LARGE_NET", "Large net", 10, action(REVIEW), predicate("root", null, 1, "GT",
                variableOperand("INPUT_NET_AMOUNT", "NUMBER"), money(threshold))))
            .build());
    }

    /** Client 1 小时评估次数达到阈值审核的冻结结账规则集。 */
    private long clientCountRuleSet(String variableCode, String threshold) {
        return frozenRuleSet(RuleSetCreateRequestBuilder.template(checkoutCode("BLOCK")).pointCode(CHECKOUT)
            .rules(rule("CLIENT_COUNT", "Client count", 10, action(REVIEW), predicate("root", null, 1, ComparisonOperator.GTE.getCode(),
                variableOperand(variableCode, DecisionValueType.NUMBER.getCode()), scalar(threshold))))
            .build());
    }

    /** 按当前管理版本整体删除规则集。 */
    private EntityExchangeResult<byte[]> deleteRuleSet(Map<String, Object> root) {
        HttpHeaders headers = headers();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(HttpMethod.DELETE, "/decision-rule-sets/v1/rule-sets/" + root.get("rule_set_id"), headers,
            ("{\"expectedVersion\":\"" + root.get("version") + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    /** 轮询发布直到影子批次结束；有上限的条件等待。 */
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

    /** 创建结账执行发布。 */
    private ExecutionReleaseResponseChecker create(long flowVersionId) {
        CreateExecutionReleaseRequestBody body = new CreateExecutionReleaseRequestBody();
        body.setPointCode(CHECKOUT);
        body.setFlowVersionId(Long.toString(flowVersionId));
        return post(EXECUTIONS, body);
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
    private ExecutionReleaseResponseChecker get(long releaseId) {
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
}

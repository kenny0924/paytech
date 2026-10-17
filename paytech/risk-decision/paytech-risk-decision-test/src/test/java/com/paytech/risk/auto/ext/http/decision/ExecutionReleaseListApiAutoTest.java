package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.action;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.predicate;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.rule;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.api.decision.ExecutionReleaseManagementApi;
import com.paytech.risk.api.decision.request.CreateExecutionReleaseRequestBody;
import com.paytech.risk.api.decision.request.StartShadowRunRequestBody;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.enums.ShadowRunStatus;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.builder.decision.ReleaseBuilder;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.PaymentRiskRequestBuilder;
import com.paytech.risk.test.checker.decision.ExecutionReleaseResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 执行发布分页列表：只列经决策流的执行发布，按创建时间、发布 ID 倒序
 * <li>正常流程：首发生效、第二个发布影子后替代、第三个发布只创建；列表给出状态、是否生效、所冻结的流程编码与版本、创建人与激活人；按状态筛选得到当前生效发布；分页。</li>
 * <li>无数据：没有执行发布的风控点返回空页；结账旧候选发布不出现在列表中。</li>
 * <li>参数：缺少或未登记的风控点、未知状态、不允许的每页条数、未声明的 Query 参数返回 400。</li>
 *
 * @see ExecutionReleaseManagementApi#listExecutions(String, String, Integer, Integer, String, String)
 */
class ExecutionReleaseListApiAutoTest extends AbstractPaymentRiskApiTest {
    private static final String LIST = EXECUTIONS + "?pointCode=" + PAYMENT;

    private static final long SHADOW_WAIT_MILLIS = 15_000;

    private String reason;

    private long blockRuleSet;

    /** 每例准备一个金额审核拦截规则集。 */
    @BeforeEach
    void setUpClassMocks() {
        reason = rejectionReason();
        blockRuleSet = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_BLOCK")).pointCode(PAYMENT)
            .rules(rule("LARGE_AMOUNT", "Large amount", 10, action("REVIEW_PAYMENT"), predicate("root", null, 1, "GT",
                variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money("1000"))))
            .build());
    }

    /** 生效、替代、草稿三个发布：倒序、状态、生效标记、流程坐标与激活人；按状态筛选与分页。 */
    @Test
    void test_normalFlow() {
        String firstFlow = newCode("T_FLOW");
        long first = activeRelease(saveFlow(firstFlow, twoStageGraph(blockRuleSet, authRuleSet("500"), reason)));
        assess(PaymentRiskRequestBuilder.template().amount("600").build());
        String secondFlow = newCode("T_FLOW");
        long second = validatedRelease(saveFlow(secondFlow, twoStageGraph(blockRuleSet, authRuleSet("50"), reason)));
        shadowAndActivate(second);
        long draft = create(saveFlow(newCode("T_FLOW"), twoStageGraph(blockRuleSet, authRuleSet("100"), reason)));

        JsonNode page = getOk(LIST);

        JsonNode items = page.path("items");
        assertAll("列表",
            () -> assertEquals(3, page.path("total").asLong()),
            () -> assertEquals(List.of(draft, second, first), ids(items)),
            () -> assertItem(items.path(0), DecisionReleaseStatus.DRAFT, false, null),
            () -> assertItem(items.path(1), DecisionReleaseStatus.ACTIVE, true, ACTOR_ID),
            () -> assertItem(items.path(2), DecisionReleaseStatus.SUPERSEDED, false, ACTOR_ID),
            () -> assertEquals(secondFlow, items.path(1).path("flowCode").asString()),
            () -> assertEquals(firstFlow, items.path(2).path("flowCode").asString()),
            () -> assertEquals(1, items.path(1).path("flowVersionNo").asInt()),
            () -> assertFalse(items.path(2).path("supersededTime").isNull(), "被替代时间"),
            () -> assertTrue(items.path(0).path("activatedTime").isNull(), "草稿无激活时间"));

        JsonNode active = getOk(LIST + "&status=" + DecisionReleaseStatus.ACTIVE.getCode());
        assertEquals(List.of(second), ids(active.path("items")));
        JsonNode secondPage = getOk(LIST + "&page=2&size=10");
        assertAll("分页", () -> assertEquals(3, secondPage.path("total").asLong()),
            () -> assertEquals(0, secondPage.path("items").size()));
    }

    /** 没有执行发布返回空页；结账旧候选发布不在执行发布列表中。 */
    @Test
    void test_noDataFound() {
        ruleSets.mockRelease(ruleSets.mock(RuleSetBuilder.versions(RuleSetVersionStatus.FROZEN)), 1,
            ReleaseBuilder.of(DecisionEnvironment.TEST, DecisionReleaseStatus.DRAFT));

        JsonNode payment = getOk(LIST);
        JsonNode checkout = getOk(EXECUTIONS + "?pointCode=CHECKOUT_PREPARE");

        assertAll("空页", () -> assertEquals(0, payment.path("total").asLong()),
            () -> assertEquals(0, payment.path("items").size()), () -> assertEquals(0, checkout.path("total").asLong()));
    }

    /** 缺少或未登记的风控点、未知状态、不允许的每页条数。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"", "?pointCode=UNKNOWN_POINT", "?pointCode=PAYMENT_ATTEMPT_RISK&status=UNKNOWN",
        "?pointCode=PAYMENT_ATTEMPT_RISK&size=7", "?pointCode=payment", "?pointCode=PAYMENT_ATTEMPT_RISK&flowCode=X"})
    void test_invalidParameter(String query) {
        HttpHeaders headers = headers();
        new ExecutionReleaseResponseChecker(http.get(EXECUTIONS + query, headers), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID)).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 列表项的状态、生效标记、激活人与公共字段。 */
    private static void assertItem(JsonNode item, DecisionReleaseStatus status, boolean active, Long activatedBy) {
        assertAll(status.getCode(),
            () -> assertEquals(status.getCode(), item.path("status").asString()),
            () -> assertEquals(active, item.path("active").asBoolean()),
            () -> assertEquals(PAYMENT, item.path("pointCode").asString()),
            () -> assertEquals(DecisionEnvironment.TEST.getCode(), item.path("environment").asString()),
            () -> assertEquals(Long.toString(ACTOR_ID), item.path("createdBy").asString()),
            () -> assertEquals(activatedBy == null ? null : activatedBy.toString(),
                item.path("activatedBy").isNull() ? null : item.path("activatedBy").asString()),
            () -> assertTrue(item.path("flowVersionId").asString().matches("[1-9][0-9]*"), "flowVersionId"),
            () -> assertTrue(item.path("contentHash").asString().matches("[0-9a-f]{64}"), "contentHash"));
    }

    /** 当前页的发布 ID。 */
    private static List<Long> ids(JsonNode items) {
        List<Long> ids = new ArrayList<>();
        items.forEach(item -> ids.add(Long.parseLong(item.path("releaseId").asString())));
        return ids;
    }

    /** 金额超过阈值时要求 3DS 的认证规则集。 */
    private long authRuleSet(String threshold) {
        return frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_AUTH")).pointCode(PAYMENT)
            .rules(rule("THREE_DS_AMOUNT", "3DS amount", 10, action("ALLOW_PAYMENT"),
                predicate("root", null, 1, "GT", variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money(threshold))))
            .build());
    }

    /** 只创建执行发布，返回发布 ID。 */
    private long create(long flowVersionId) {
        CreateExecutionReleaseRequestBody body = new CreateExecutionReleaseRequestBody();
        body.setPointCode(PAYMENT);
        body.setFlowVersionId(Long.toString(flowVersionId));
        return Long.parseLong(postOk(EXECUTIONS, body).path("releaseId").asString());
    }

    /** 影子运行完成后激活候选。 */
    private void shadowAndActivate(long releaseId) {
        StartShadowRunRequestBody body = new StartShadowRunRequestBody();
        body.setSampleTarget(10);
        postOk(EXECUTIONS + "/" + releaseId + "/shadow-runs", body);
        long deadline = System.currentTimeMillis() + SHADOW_WAIT_MILLIS;
        while (ShadowRunStatus.RUNNING.getCode().equals(getOk(EXECUTIONS + "/" + releaseId).path("shadowRun")
            .path("status").asString())) {
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
        postOk(EXECUTIONS + "/" + releaseId + "/activate", "{}");
    }

    /** GET 并要求 200。 */
    private JsonNode getOk(String path) {
        var result = http.get(path, headers());
        assertEquals(200, result.getStatus().value(), () -> path + " " + new String(result.getResponseBody()));
        return objectMapper.readTree(result.getResponseBody());
    }
}

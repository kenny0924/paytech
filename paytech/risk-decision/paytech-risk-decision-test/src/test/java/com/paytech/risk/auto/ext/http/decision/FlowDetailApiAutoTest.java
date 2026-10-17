package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.domain.enums.RuleSetManagementStatus.CONFIGURABLE;
import static com.paytech.risk.decision.domain.enums.RuleSetManagementStatus.DELETED;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.DRAFT;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.FROZEN;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.decision.DecisionFlowQueryApi;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.builder.decision.FlowBuilder;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.checker.decision.FlowDetailResponseChecker;
import com.paytech.risk.test.checker.decision.FlowDetailResponseChecker.ExpectedReference;
import com.paytech.risk.test.mapper.decision.FlowTestMapper;
import com.paytech.risk.test.mocker.decision.FlowMocker;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlow;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

/**
 * 流程当前配置与准确历史版本详情
 * <li>正常流程：当前入口返回头版本，准确入口返回所选版本；根事实始终取自当前根，图逐字段原样重建。</li>
 * <li>引用与变量：规则集节点给出准确版本及当前管理资格（含已删除规则集）；条件引用的共享变量给出查询时的当前值。</li>
 * <li>未找到：流程不存在、当前入口遇到已删除流程、版本不存在或属于其他流程均返回 404；已删除流程仍可读准确历史。</li>
 * <li>参数：路径 ID 非正 BIGINT、任意 Query 参数及 GET Body 在查询前拒绝（400）。</li>
 * <li>闭包损坏：根修订号、历史版本合同或条件存储内容损坏时失败关闭（500）。</li>
 *
 * @see DecisionFlowQueryApi#current(String, String, String)
 * @see DecisionFlowQueryApi#version(String, String, String, String)
 */
class FlowDetailApiAutoTest extends MockTestBase {
    private static final String CURRENT = "/decision-flows/v1/flows/%s";

    private static final String VERSION = "/decision-flows/v1/flows/%s/versions/%s";

    @Autowired
    private FlowMocker flows;

    @Autowired
    private RuleSetMocker ruleSets;

    @Autowired
    private FlowTestMapper flowTestMapper;

    /** 两个版本的流程，当前入口返回第二版，不引用规则集或变量。 */
    @Test
    void test_normalFlow_current() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(GraphSpec.direct(DecisionValue.ALLOW),
            GraphSpec.direct(DecisionValue.REVIEW)));
        Instant before = now();

        FlowDetailResponseChecker response = current(flow.flowId());

        response.assertDetail(flow, 2).assertReferences().assertNoVariables(before, now());
    }

    /** 准确入口返回所选历史版本的图与保存事实，根事实仍指向当前版本。 */
    @Test
    void test_normalFlow_historicalVersion() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(GraphSpec.deny("MOCK_HISTORIC_REASON", "Historic message"),
            GraphSpec.direct(DecisionValue.ALLOW)));
        Instant before = now();

        FlowDetailResponseChecker response = version(flow.flowId(), flow.version(1).flowVersionId());

        response.assertDetail(flow, 1).assertReferences().assertNoVariables(before, now());
    }

    /** 规则集节点按节点键顺序给出准确引用；规则集整体删除后历史引用仍保留并标记 DELETED。 */
    @Test
    void test_normalFlow_ruleSetReferences() {
        MockedRuleSet configurable = ruleSets.mock(versions(FROZEN, DRAFT));
        MockedRuleSet deleted = ruleSets.mock(versions(FROZEN).deleted());
        MockedFlow flow = flows.mock(FlowBuilder.versions(GraphSpec.through(configurable.version(1).ruleSetVersionId(),
            deleted.version(1).ruleSetVersionId())));
        Instant before = now();

        FlowDetailResponseChecker response = current(flow.flowId());

        response.assertDetail(flow, 1)
            .assertReferences(new ExpectedReference("rules_1", configurable, 1, CONFIGURABLE),
                new ExpectedReference("rules_2", deleted, 1, DELETED))
            .assertNoVariables(before, now());
    }

    /** 条件出口引用 NODE_MATCHED 时返回该共享变量在查询时的当前投影。 */
    @Test
    void test_normalFlow_conditionVariables() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT));
        MockedFlow flow = flows.mock(FlowBuilder.versions(GraphSpec.branching(ruleSet.currentVersionId())));
        Instant before = now();

        FlowDetailResponseChecker response = current(flow.flowId());

        response.assertDetail(flow, 1)
            .assertReferences(new ExpectedReference("rules", ruleSet, 1, CONFIGURABLE))
            .assertNodeMatchedVariable(flowTestMapper.variableUpdateTime("NODE_MATCHED"), before, now());
    }

    /** 整体删除后准确入口仍可读取每个历史版本，根状态为 DELETED 且修订号多一次。 */
    @Test
    void test_normalFlow_deletedFlowHistory() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(2).deleted());

        version(flow.flowId(), flow.version(1).flowVersionId()).assertDetail(flow, 1);
        version(flow.flowId(), flow.version(2).flowVersionId()).assertDetail(flow, 2);
    }

    /** 流程不存在、当前入口遇到已删除流程、版本不存在或属于其他流程。 */
    @Test
    void test_noDataFound() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(2));
        MockedFlow other = flows.mock(FlowBuilder.versions(1));
        MockedFlow deleted = flows.mock(FlowBuilder.versions(1).deleted());

        current(deleted.flowId()).assertNotFound();
        current(999_999_999_999_999_999L).assertNotFound();
        version(999_999_999_999_999_999L, flow.current().flowVersionId()).assertNotFound();
        version(flow.flowId(), 999_999_999_999_999_999L).assertNotFound();
        version(flow.flowId(), other.current().flowVersionId()).assertNotFound();
    }

    /** 当前入口路径中的流程 ID 格式非法。 */
    @ParameterizedTest(name = "flowId={0}")
    @MethodSource("invalidIds")
    void test_invalidParameter_currentFlowId(String flowId) {
        invoke(headers(), CURRENT.formatted(flowId)).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 准确入口路径中的流程 ID 或版本 ID 格式非法。 */
    @ParameterizedTest(name = "id={0}")
    @MethodSource("invalidIds")
    void test_invalidParameter_versionIds(String id) {
        MockedFlow flow = flows.mock(FlowBuilder.versions(1));

        invoke(headers(), VERSION.formatted(id, flow.current().flowVersionId())).assertProblem(400,
            INVALID_REQUEST.getCode());
        invoke(headers(), VERSION.formatted(flow.flowId(), id)).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 详情入口不声明任何 Query 参数。 */
    @Test
    void test_invalidParameter_query() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(1));

        invoke(headers(), CURRENT.formatted(flow.flowId()) + "?versionNo=1").assertProblem(400,
            INVALID_REQUEST.getCode());
        invoke(headers(), VERSION.formatted(flow.flowId(), flow.current().flowVersionId()) + "?page=1")
            .assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(1));
        String requestId = nextRequestId();

        new FlowDetailResponseChecker(http.exchange(HttpMethod.GET, CURRENT.formatted(flow.flowId()),
            headers(requestId), "{}"), objectMapper, requestId).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 根修订号与当前版本号不一致时两个入口都失败关闭。 */
    @Test
    void test_businessRuleViolation_corruptedRoot() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(2));
        flows.corruptRootVersion(flow);

        current(flow.flowId()).assertProblem(500, INTERNAL_ERROR.getCode());
        version(flow.flowId(), flow.version(1).flowVersionId()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 历史版本归属的输入合同被改写时该版本失败关闭，当前版本不受影响。 */
    @Test
    void test_businessRuleViolation_corruptedHistoricalVersion() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(2));
        flows.corruptVersionContract(flow.version(1));

        version(flow.flowId(), flow.version(1).flowVersionId()).assertProblem(500, INTERNAL_ERROR.getCode());
        current(flow.flowId()).assertDetail(flow, 2);
    }

    /** 连线条件存储内容不可读时失败关闭，不回退为空条件。 */
    @Test
    void test_businessRuleViolation_corruptedCondition() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT));
        MockedFlow flow = flows.mock(FlowBuilder.versions(GraphSpec.branching(ruleSet.currentVersionId())));
        flows.corruptCondition(flow.current(), "matched");

        current(flow.flowId()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    static Stream<String> invalidIds() {
        return Stream.of("0", "-1", "abc", "012", "1".repeat(20), "9223372036854775808");
    }

    private FlowDetailResponseChecker current(long flowId) {
        return invoke(headers(), CURRENT.formatted(flowId));
    }

    private FlowDetailResponseChecker version(long flowId, long flowVersionId) {
        return invoke(headers(), VERSION.formatted(flowId, flowVersionId));
    }

    private FlowDetailResponseChecker invoke(HttpHeaders headers, String uri) {
        String requestId = headers.getFirst(ApiHeaderNames.REQUEST_ID);
        return new FlowDetailResponseChecker(http.get(uri, headers), objectMapper, requestId);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static HttpHeaders headers() {
        return headers(nextRequestId());
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }

    private static String nextRequestId() {
        return "risk-flow-detail-" + UUID.randomUUID();
    }
}

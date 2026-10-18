package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.decision.api.DecisionFlowQueryApi;
import com.paytech.risk.test.base.RiskDecisionMockTestBase;
import com.paytech.risk.test.builder.decision.FlowBuilder;
import com.paytech.risk.test.checker.decision.FlowHistoryResponseChecker;
import com.paytech.risk.test.mocker.decision.FlowMocker;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlow;

/**
 * 流程不可变历史分页
 * <li>正常流程：固定业务版本上界内按版本号倒序分页；整体删除的流程仍可浏览历史，其他流程的版本不混入。</li>
 * <li>未找到：流程不存在返回 404。</li>
 * <li>参数：路径 ID、上界（缺失/非正/超出当前版本/非数字/重复/空值）、分页、未知 Query 与 GET Body 在查询前拒绝（400）。</li>
 * <li>闭包损坏：根修订号或当前页任一版本的归属合同不符时失败关闭（500）；不在当前页的版本不影响浏览。</li>
 *
 * @see DecisionFlowQueryApi#history(String, String, String, Integer, Integer, Integer)
 */
class FlowHistoryApiAutoTest extends RiskDecisionMockTestBase {
    private static final String PATH = "/decision-flows/v1/flows/%s/versions";

    @Autowired
    private FlowMocker flows;

    /** 三个版本，上界为当前版本时全部按版本号倒序返回。 */
    @Test
    void test_normalFlow() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(3));

        read(flow, "maxVersionNo=3").assertPage(1, 10, 3).assertVersions(flow, 3, 2, 1);
    }

    /** 上界低于当前版本时只返回上界内版本。 */
    @Test
    void test_normalFlow_upperBoundExcludesNewerVersions() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(3));

        read(flow, "maxVersionNo=2").assertPage(1, 10, 2).assertVersions(flow, 2, 1);
        read(flow, "maxVersionNo=1").assertPage(1, 10, 1).assertVersions(flow, 1);
    }

    /** 分页按版本号倒序切分，越界页返回空列表但保留总数。 */
    @Test
    void test_normalFlow_paging() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(11));

        read(flow, "maxVersionNo=11&page=1&size=10").assertPage(1, 10, 11).assertVersions(flow, 11, 10, 9, 8, 7, 6, 5,
            4, 3, 2);
        read(flow, "maxVersionNo=11&page=2&size=10").assertPage(2, 10, 11).assertVersions(flow, 1);
        read(flow, "maxVersionNo=11&page=3").assertPage(3, 10, 11).assertVersions(flow);
        read(flow, "maxVersionNo=11&size=20").assertPage(1, 20, 11).assertVersions(flow, 11, 10, 9, 8, 7, 6, 5, 4, 3,
            2, 1);
    }

    /** 整体删除的流程仍可在当前最高业务版本内浏览历史。 */
    @Test
    void test_normalFlow_deletedFlow() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(2).deleted());

        read(flow, "maxVersionNo=2").assertPage(1, 10, 2).assertVersions(flow, 2, 1);
    }

    /** 其他流程的版本不混入本流程历史。 */
    @Test
    void test_normalFlow_otherFlowsAreIsolated() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(2));
        flows.mock(FlowBuilder.versions(3));

        read(flow, "maxVersionNo=2").assertPage(1, 10, 2).assertVersions(flow, 2, 1);
    }

    /** 流程不存在。 */
    @Test
    void test_noDataFound() {
        invoke(headers(), PATH.formatted("999999999999999999") + "?maxVersionNo=1").assertNotFound();
    }

    /** 查询串非法时在查询前拒绝；流程本身存在且合法。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        MockedFlow flow = flows.mock(FlowBuilder.versions(3));

        read(flow, query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 已删除流程的上界也不能超过当前最高业务版本。 */
    @Test
    void test_invalidParameter_deletedFlowUpperBound() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(2).deleted());

        read(flow, "maxVersionNo=3").assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 路径中的流程 ID 格式非法。 */
    @ParameterizedTest(name = "flowId={0}")
    @MethodSource("invalidFlowIds")
    void test_invalidParameter_flowId(String flowId) {
        invoke(headers(), PATH.formatted(flowId) + "?maxVersionNo=1").assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(1));
        String requestId = nextRequestId();

        new FlowHistoryResponseChecker(http.exchange(HttpMethod.GET,
            PATH.formatted(flow.flowId()) + "?maxVersionNo=1", headers(requestId), "{}"), objectMapper, requestId)
            .assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 根修订号与当前版本不一致时失败关闭。 */
    @Test
    void test_businessRuleViolation_corruptedRoot() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(2));
        flows.corruptRootVersion(flow);

        read(flow, "maxVersionNo=2").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 当前页内任一历史版本的归属合同损坏时整体失败，不返回被篡改的历史。 */
    @Test
    void test_businessRuleViolation_corruptedHistoricalVersion() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(11));
        flows.corruptVersionContract(flow.version(1));

        read(flow, "maxVersionNo=11&page=2").assertProblem(500, INTERNAL_ERROR.getCode());
        read(flow, "maxVersionNo=11&size=20").assertProblem(500, INTERNAL_ERROR.getCode());
        // 损坏版本不在当前页时只校验本页版本，仍可浏览。
        read(flow, "maxVersionNo=11&page=1").assertPage(1, 10, 11).assertVersions(flow, 11, 10, 9, 8, 7, 6, 5, 4, 3,
            2);
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("缺少 maxVersionNo", ""), Arguments.of("maxVersionNo 为 0", "maxVersionNo=0"),
            Arguments.of("maxVersionNo 为负数", "maxVersionNo=-1"),
            Arguments.of("maxVersionNo 超过当前版本", "maxVersionNo=4"),
            Arguments.of("maxVersionNo 非数字", "maxVersionNo=two"), Arguments.of("maxVersionNo 为空值", "maxVersionNo="),
            Arguments.of("maxVersionNo 前导零", "maxVersionNo=01"),
            Arguments.of("maxVersionNo 超过整数范围", "maxVersionNo=2147483648"),
            Arguments.of("maxVersionNo 重复", "maxVersionNo=1&maxVersionNo=1"),
            Arguments.of("page 为 0", "maxVersionNo=1&page=0"),
            Arguments.of("page 超过 100000", "maxVersionNo=1&page=100001"),
            Arguments.of("size 不在允许值内", "maxVersionNo=1&size=15"),
            Arguments.of("未知参数", "maxVersionNo=1&status=DRAFT"));
    }

    static Stream<String> invalidFlowIds() {
        return Stream.of("0", "-1", "abc", "012", "1".repeat(20), "9223372036854775808");
    }

    private FlowHistoryResponseChecker read(MockedFlow flow, String query) {
        String path = PATH.formatted(flow.flowId());
        return invoke(headers(), query.isEmpty() ? path : path + "?" + query);
    }

    private FlowHistoryResponseChecker invoke(HttpHeaders headers, String uri) {
        String requestId = headers.getFirst(ApiHeaderNames.REQUEST_ID);
        return new FlowHistoryResponseChecker(http.get(uri, headers), objectMapper, requestId);
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
        return "risk-flow-history-" + UUID.randomUUID();
    }
}

package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_FLOW_DELETED;
import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_FLOW_VERSION_CONFLICT;
import static com.paytech.risk.decision.domain.enums.FlowStatus.CONFIGURABLE;
import static com.paytech.risk.decision.domain.enums.FlowStatus.DELETED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
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
import com.paytech.risk.decision.api.DecisionFlowManagementApi;
import com.paytech.risk.test.base.RiskDecisionMockTestBase;
import com.paytech.risk.test.builder.decision.FlowBuilder;
import com.paytech.risk.test.checker.decision.DeleteFlowResponseChecker;
import com.paytech.risk.test.checker.decision.FlowStoreChecker;
import com.paytech.risk.test.checker.decision.FlowStoreChecker.ExpectedRoot;
import com.paytech.risk.test.mapper.decision.FlowTestMapper;
import com.paytech.risk.test.mocker.decision.FlowMocker;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlow;

/**
 * 按根修订号整体删除流程
 * <li>正常流程：根状态改为 DELETED、修订号加一并记录删除人与时间，编码与全部不可变版本保留。</li>
 * <li>未找到：流程不存在返回 404。</li>
 * <li>参数：路径 ID、expectedVersion（缺失/非正/非数字/前导零/空值/重复）、未知 Query 与请求 Body 在写入前拒绝（400）。</li>
 * <li>业务规则：过期修订号返回版本冲突，已删除流程再次删除返回 RISK_FLOW_DELETED（409）；根闭包损坏时失败关闭（500）。</li>
 * <li>并发：同一修订号的两次删除只有一次成功，删除事实只写一次。</li>
 *
 * @see DecisionFlowManagementApi#delete(String, String, String, String)
 */
class FlowDeleteApiAutoTest extends RiskDecisionMockTestBase {
    private static final String PATH = "/decision-flows/v1/flows/%s";

    private static final long ACTOR = 70001L;

    @Autowired
    private FlowMocker flows;

    @Autowired
    private FlowStoreChecker store;

    @Autowired
    private FlowTestMapper flowTestMapper;

    /** 删除当前修订号的流程：根改为 DELETED，修订号加一，版本历史保持不变。 */
    @Test
    void test_normalFlow() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(2));
        Instant before = now();

        Instant deletedTime = delete(flow.flowId(), "2").assertDeleted(flow.flowId(), 3, ACTOR, before, now());

        store.assertFlow(flow.flowCode(), new ExpectedRoot(flow.flowId(), flow.current().flowVersionId(), DELETED, 3,
            ACTOR, deletedTime, flow.createTime(), deletedTime), flow.versions().stream()
                .map(FlowStoreChecker::inserted).toList());
    }

    /** 流程不存在。 */
    @Test
    void test_noDataFound() {
        delete(999_999_999_999_999_999L, "1").assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }

    /** 查询串非法时在删除前拒绝，流程不变。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        MockedFlow flow = flows.mock(FlowBuilder.versions(1));

        invoke(headers(nextRequestId()), PATH.formatted(flow.flowId()) + query, null).assertProblem(400,
            INVALID_REQUEST.getCode());

        store.assertUnchanged(flow);
    }

    /** 路径中的流程 ID 格式非法。 */
    @ParameterizedTest(name = "flowId={0}")
    @MethodSource("invalidFlowIds")
    void test_invalidParameter_flowId(String flowId) {
        invoke(headers(nextRequestId()), PATH.formatted(flowId) + "?expectedVersion=1", null).assertProblem(400,
            INVALID_REQUEST.getCode());
    }

    /** 删除请求不接受 Body。 */
    @Test
    void test_invalidParameter_body() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(1));

        invoke(headers(nextRequestId()), PATH.formatted(flow.flowId()) + "?expectedVersion=1", "{}")
            .assertProblem(400, INVALID_REQUEST.getCode());

        store.assertUnchanged(flow);
    }

    /** 修订号已过期（低于或高于当前）时冲突，流程不变。 */
    @Test
    void test_businessRuleViolation_staleVersion() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(2));

        delete(flow.flowId(), "1").assertProblem(409, RISK_FLOW_VERSION_CONFLICT.getCode());
        delete(flow.flowId(), "3").assertProblem(409, RISK_FLOW_VERSION_CONFLICT.getCode());

        store.assertUnchanged(flow);
    }

    /** 已删除流程不能再次删除，不产生第二份删除事实。 */
    @Test
    void test_businessRuleViolation_alreadyDeleted() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(1).deleted());

        delete(flow.flowId(), Long.toString(flow.version())).assertProblem(409, RISK_FLOW_DELETED.getCode());
        delete(flow.flowId(), "1").assertProblem(409, RISK_FLOW_DELETED.getCode());

        store.assertUnchanged(flow);
    }

    /** 根修订号与当前版本不一致时失败关闭。 */
    @Test
    void test_businessRuleViolation_corruptedRoot() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(1));
        flows.corruptRootVersion(flow);

        delete(flow.flowId(), Long.toString(flow.version() + 5)).assertProblem(500, INTERNAL_ERROR.getCode());

        assertEquals(CONFIGURABLE.getCode(), flowTestMapper.root(flow.flowCode()).orElseThrow().status());
    }

    /** 同一修订号的两次并发删除只有一次成功，另一项冲突。 */
    @Test
    void test_edgeCase_concurrentDeletes() throws Exception {
        MockedFlow flow = flows.mock(FlowBuilder.versions(1));
        CyclicBarrier ready = new CyclicBarrier(2);
        List<CompletableFuture<DeleteFlowResponseChecker>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(CompletableFuture.supplyAsync(() -> {
                await(ready);
                return delete(flow.flowId(), "1");
            }));
        }
        List<DeleteFlowResponseChecker> responses = new ArrayList<>();
        for (CompletableFuture<DeleteFlowResponseChecker> result : results) {
            responses.add(result.get(30, TimeUnit.SECONDS));
        }

        responses.sort((a, b) -> Integer.compare(a.status(), b.status()));
        assertEquals(List.of(200, 409), responses.stream().map(DeleteFlowResponseChecker::status).toList());
        Instant deletedTime = responses.get(0).assertDeleted(flow.flowId(), 2, ACTOR, flow.createTime(), now());
        // 后到者在锁内看到已删除的根，或等待锁超时（并发冲突）。
        responses.get(1).assertProblemIn(409, RISK_FLOW_DELETED.getCode(), CONCURRENT_REQUEST.getCode());
        store.assertFlow(flow.flowCode(), new ExpectedRoot(flow.flowId(), flow.current().flowVersionId(), DELETED, 2,
            ACTOR, deletedTime, flow.createTime(), deletedTime), List.of(FlowStoreChecker.inserted(flow.current())));
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("缺少 expectedVersion", ""), Arguments.of("expectedVersion 为 0", "?expectedVersion=0"),
            Arguments.of("expectedVersion 为负数", "?expectedVersion=-1"),
            Arguments.of("expectedVersion 非数字", "?expectedVersion=one"),
            Arguments.of("expectedVersion 前导零", "?expectedVersion=01"),
            Arguments.of("expectedVersion 为空值", "?expectedVersion="),
            Arguments.of("expectedVersion 重复", "?expectedVersion=1&expectedVersion=1"),
            Arguments.of("未知参数", "?expectedVersion=1&reason=cleanup"));
    }

    static Stream<String> invalidFlowIds() {
        return Stream.of("0", "-1", "abc", "012", "1".repeat(20), "9223372036854775808");
    }

    private DeleteFlowResponseChecker delete(long flowId, String expectedVersion) {
        return invoke(headers(nextRequestId()), PATH.formatted(flowId) + "?expectedVersion=" + expectedVersion, null);
    }

    private DeleteFlowResponseChecker invoke(HttpHeaders headers, String uri, String body) {
        return new DeleteFlowResponseChecker(http.exchange(HttpMethod.DELETE, uri, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACTOR));
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }

    private static String nextRequestId() {
        return "risk-flow-delete-" + UUID.randomUUID();
    }
}

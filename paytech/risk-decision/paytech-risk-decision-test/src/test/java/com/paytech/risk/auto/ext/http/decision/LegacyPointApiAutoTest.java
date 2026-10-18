package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.domain.enums.RuleSetManagementStatus.CONFIGURABLE;
import static com.paytech.risk.decision.domain.enums.RuleSetManagementStatus.DELETED;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.DRAFT;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.FROZEN;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;

import java.util.List;
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
import com.paytech.risk.decision.api.DecisionFlowQueryApi;
import com.paytech.risk.test.base.RiskDecisionMockTestBase;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.checker.decision.LegacyPointResponseChecker;
import com.paytech.risk.test.mapper.decision.FlowTestMapper;
import com.paytech.risk.test.mocker.decision.FlowMocker;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedLegacyPoint;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

/**
 * 旧点版本绑定的只读顺序图投影
 * <li>正常流程：按绑定序号展开为 开始 → 规则集节点 → 原默认决定 的顺序图，引用给出准确规则版本及当前管理状态（含已删除规则集）。</li>
 * <li>边界：没有任何绑定时开始直连原默认决定。</li>
 * <li>未找到：旧点版本不存在返回 404。</li>
 * <li>参数：路径 ID 非正 BIGINT、任意 Query 参数与 GET Body 在查询前拒绝（400）。</li>
 * <li>闭包损坏：绑定序号断档或被引用规则版本合同不符时失败关闭（500），不返回部分顺序图。</li>
 *
 * @see DecisionFlowQueryApi#legacyPoint(String, String, String)
 */
class LegacyPointApiAutoTest extends RiskDecisionMockTestBase {
    private static final String PATH = "/decision-flows/v1/legacy-point-versions/%s";

    @Autowired
    private FlowMocker flows;

    @Autowired
    private RuleSetMocker ruleSets;

    @Autowired
    private FlowTestMapper flowTestMapper;

    /** 两个绑定按序号展开；第二个规则集已整体删除，引用状态为 DELETED。 */
    @Test
    void test_normalFlow() {
        MockedRuleSet first = ruleSets.mock(versions(FROZEN));
        MockedRuleSet second = ruleSets.mock(versions(FROZEN, DRAFT).deleted());
        MockedLegacyPoint point = flows.mockLegacyPoint(first.version(1), second.version(1));

        read(point.pointVersionId()).assertProjection(point, List.of(first, second), List.of(CONFIGURABLE, DELETED));
    }

    /** 没有绑定时开始直接连到原默认决定。 */
    @Test
    void test_edgeCase_noBindings() {
        MockedLegacyPoint point = flows.mockLegacyPoint();

        read(point.pointVersionId()).assertProjection(point, List.of(), List.of());
    }

    /** 旧点版本不存在。 */
    @Test
    void test_noDataFound() {
        read(999_999_999_999_999_999L).assertNotFound();
    }

    /** 路径中的点版本 ID 格式非法。 */
    @ParameterizedTest(name = "pointVersionId={0}")
    @MethodSource("invalidIds")
    void test_invalidParameter_pointVersionId(String pointVersionId) {
        invoke(headers(), PATH.formatted(pointVersionId)).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 投影入口不声明任何 Query 参数。 */
    @Test
    void test_invalidParameter_query() {
        MockedLegacyPoint point = flows.mockLegacyPoint();

        invoke(headers(), PATH.formatted(point.pointVersionId()) + "?page=1").assertProblem(400,
            INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        MockedLegacyPoint point = flows.mockLegacyPoint();
        String requestId = nextRequestId();

        new LegacyPointResponseChecker(http.exchange(HttpMethod.GET, PATH.formatted(point.pointVersionId()),
            headers(requestId), "{}"), objectMapper, requestId).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 绑定序号不从 1 连续时失败关闭。 */
    @Test
    void test_businessRuleViolation_bindingGap() {
        MockedRuleSet first = ruleSets.mock(versions(FROZEN));
        MockedRuleSet second = ruleSets.mock(versions(FROZEN));
        MockedLegacyPoint point = flows.mockLegacyPoint(new int[] {1, 3}, first.version(1), second.version(1));

        read(point.pointVersionId()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 被引用规则版本的输入合同与旧点版本不一致时失败关闭。 */
    @Test
    void test_businessRuleViolation_contractMismatch() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN));
        MockedLegacyPoint point = flows.mockLegacyPoint(ruleSet.version(1));
        flowTestMapper.updateRuleSetContract(ruleSet.currentVersionId(), RuleSetBuilder.UNREGISTERED_CONTRACT_VERSION);

        read(point.pointVersionId()).assertProblem(500, INTERNAL_ERROR.getCode());
    }

    static Stream<String> invalidIds() {
        return Stream.of("0", "-1", "abc", "012", "1".repeat(20), "9223372036854775808");
    }

    private LegacyPointResponseChecker read(long pointVersionId) {
        return invoke(headers(), PATH.formatted(pointVersionId));
    }

    private LegacyPointResponseChecker invoke(HttpHeaders headers, String uri) {
        String requestId = headers.getFirst(ApiHeaderNames.REQUEST_ID);
        return new LegacyPointResponseChecker(http.get(uri, headers), objectMapper, requestId);
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
        return "risk-legacy-point-" + UUID.randomUUID();
    }
}

package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.DRAFT;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.FROZEN;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import com.paytech.risk.api.decision.DecisionFlowQueryApi;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.builder.decision.FlowBuilder;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.checker.decision.FlowListResponseChecker;
import com.paytech.risk.test.mocker.decision.FlowMocker;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlow;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

/**
 * 流程列表分页查询
 * <li>正常流程：每个可配置流程根一行，按根更新时间、流程 ID 倒序；摘要取自当前版本，规则集节点数按当前图统计。</li>
 * <li>筛选：编码精确匹配；名称只匹配当前版本，按字面包含（% 与 _ 不作通配符）；已删除流程不出现。</li>
 * <li>未找到：无匹配返回空页；决策点不受支持返回 404。</li>
 * <li>参数：决策点/编码格式、名称首尾空白与长度、分页、未知/重复/空 Query 与 GET Body 在查询前拒绝（400）。</li>
 * <li>业务规则：决策点停用不可配置（400）；当前版本闭包损坏时失败关闭（500）。</li>
 *
 * @see DecisionFlowQueryApi#list(String, String, String, String, String, Integer, Integer)
 */
class FlowListApiAutoTest extends MockTestBase {
    private static final String PATH = "/decision-flows/v1/flows?pointCode=CHECKOUT_PREPARE";

    private static final Instant T0 = Instant.parse("2026-06-01T00:00:00.000001Z");

    @Autowired
    private FlowMocker flows;

    @Autowired
    private RuleSetMocker ruleSets;

    /** 按根更新时间倒序；名称、版本与节点数取自当前版本，规则集节点数按当前图统计。 */
    @Test
    void test_normalFlow() {
        MockedRuleSet first = ruleSets.mock(versions(FROZEN));
        MockedRuleSet second = ruleSets.mock(versions(DRAFT));
        MockedFlow oldest = flows.mock(FlowBuilder.versions(1).updateTime(T0));
        MockedFlow newest = flows.mock(FlowBuilder.versions(GraphSpec.direct(DecisionValue.ALLOW),
            GraphSpec.through(first.currentVersionId(), second.currentVersionId())).updateTime(T0.plusSeconds(120)));
        MockedFlow middle = flows.mock(FlowBuilder.versions(GraphSpec.branching(first.currentVersionId()))
            .updateTime(T0.plusSeconds(60)));

        list("").assertPage(1, 10, 3).assertFlows(newest, middle, oldest);
    }

    /** 更新时间相同的流程按流程 ID 倒序。 */
    @Test
    void test_normalFlow_sameUpdateTimeOrderedByFlowId() {
        List<MockedFlow> mocked = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mocked.add(flows.mock(FlowBuilder.versions(1).updateTime(T0)));
        }
        mocked.sort(Comparator.comparingLong(MockedFlow::flowId).reversed());

        list("").assertPage(1, 10, 3).assertFlows(mocked);
    }

    /** 编码筛选为精确匹配。 */
    @Test
    void test_normalFlow_flowCodeFilter() {
        MockedFlow target = flows.mock(FlowBuilder.versions(2));
        flows.mock(FlowBuilder.versions(1));

        list("&flowCode=" + target.flowCode()).assertPage(1, 10, 1).assertFlows(target);
        list("&flowCode=" + target.flowCode().substring(0, 12)).assertPage(1, 10, 0).assertFlows();
    }

    /** 名称筛选只看当前版本，% 与 _ 按字面字符匹配。 */
    @Test
    void test_normalFlow_keywordFilter() {
        MockedFlow percent = flows.mock(FlowBuilder.versions(1).currentName("Rate 100% cap").updateTime(T0));
        MockedFlow digits = flows.mock(FlowBuilder.versions(1).currentName("Rate 1000 cap")
            .updateTime(T0.plusSeconds(1)));
        MockedFlow underscore = flows.mock(FlowBuilder.versions(1).currentName("Card a_1 flow")
            .updateTime(T0.plusSeconds(2)));
        flows.mock(FlowBuilder.versions(1).currentName("Card a-1 flow").updateTime(T0.plusSeconds(3)));
        MockedFlow renamed = flows.mock(FlowBuilder.versions(2).currentName("Delta current")
            .updateTime(T0.plusSeconds(4)));

        list("&keyword=100%").assertPage(1, 10, 1).assertFlows(percent);
        list("&keyword=a_1").assertPage(1, 10, 1).assertFlows(underscore);
        list("&keyword=Rate").assertPage(1, 10, 2).assertFlows(digits, percent);
        list("&keyword=Delta").assertPage(1, 10, 1).assertFlows(renamed);
        // 历史版本名称不参与匹配。
        list("&keyword=Checkout").assertPage(1, 10, 0).assertFlows();
    }

    /** 编码与名称组合筛选。 */
    @Test
    void test_normalFlow_combinedFilters() {
        MockedFlow target = flows.mock(FlowBuilder.versions(1).currentName("Combined target"));
        flows.mock(FlowBuilder.versions(1).currentName("Combined other"));

        list("&flowCode=" + target.flowCode() + "&keyword=Combined").assertPage(1, 10, 1).assertFlows(target);
        list("&flowCode=" + target.flowCode() + "&keyword=other").assertPage(1, 10, 0).assertFlows();
    }

    /** 分页按排序切分，越界页返回空列表但保留总数。 */
    @Test
    void test_normalFlow_paging() {
        List<MockedFlow> mocked = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            mocked.add(flows.mock(FlowBuilder.versions(1).updateTime(T0.plusSeconds(i))));
        }
        List<MockedFlow> expected = mocked.reversed();

        list("&page=1&size=10").assertPage(1, 10, 11).assertFlows(expected.subList(0, 10));
        list("&page=2&size=10").assertPage(2, 10, 11).assertFlows(expected.subList(10, 11));
        list("&page=3").assertPage(3, 10, 11).assertFlows();
        list("&size=20").assertPage(1, 20, 11).assertFlows(expected);
    }

    /** 整体删除的流程不出现在列表中。 */
    @Test
    void test_normalFlow_deletedExcluded() {
        MockedFlow kept = flows.mock(FlowBuilder.versions(1));
        MockedFlow deleted = flows.mock(FlowBuilder.versions(2).deleted());

        list("").assertPage(1, 10, 1).assertFlows(kept);
        list("&flowCode=" + deleted.flowCode()).assertPage(1, 10, 0).assertFlows();
    }

    /** 名称筛选恰为 100 个字符时允许。 */
    @Test
    void test_edgeCase_keywordAtLimit() {
        String name = "N".repeat(100);
        MockedFlow flow = flows.mock(FlowBuilder.versions(1).currentName(name));

        list("&keyword=" + name).assertPage(1, 10, 1).assertFlows(flow);
    }

    /** 没有任何流程时返回空页；决策点不受支持返回 404。 */
    @Test
    void test_noDataFound() {
        list("").assertPage(1, 10, 0).assertFlows();
        invoke(headers(), "/decision-flows/v1/flows?pointCode=PAYOUT_CREATION").assertNotFound();
    }

    /** 查询串非法时在查询前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String uri) {
        flows.mock(FlowBuilder.versions(1));

        invoke(headers(), uri).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        String requestId = nextRequestId();

        new FlowListResponseChecker(http.exchange(HttpMethod.GET, PATH, headers(requestId), "{}"), objectMapper,
            requestId).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 决策点停用时不可配置。 */
    @Test
    void test_businessRuleViolation_inactivePoint() {
        flows.mock(FlowBuilder.versions(1));
        flows.deactivatePoint();

        list("").assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 当前页内流程的根修订号与当前版本不一致时失败关闭。 */
    @Test
    void test_businessRuleViolation_corruptedRoot() {
        MockedFlow flow = flows.mock(FlowBuilder.versions(2));
        flows.corruptRootVersion(flow);

        list("").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    static Stream<Arguments> invalidQueries() {
        String base = "/decision-flows/v1/flows";
        return Stream.of(Arguments.of("缺少 pointCode", base), Arguments.of("pointCode 为空值", base + "?pointCode="),
            Arguments.of("pointCode 小写", base + "?pointCode=checkout_prepare"),
            Arguments.of("pointCode 重复", PATH + "&pointCode=CHECKOUT_PREPARE"),
            Arguments.of("flowCode 小写", PATH + "&flowCode=mock_flow"),
            Arguments.of("flowCode 为空值", PATH + "&flowCode="),
            Arguments.of("keyword 为空白", PATH + "&keyword=  "),
            Arguments.of("keyword 首部空白", PATH + "&keyword= Rate"),
            Arguments.of("keyword 尾部空白", PATH + "&keyword=Rate "),
            Arguments.of("keyword 超过 100 字符", PATH + "&keyword=" + "N".repeat(101)),
            Arguments.of("page 为 0", PATH + "&page=0"), Arguments.of("page 非数字", PATH + "&page=one"),
            Arguments.of("page 超过 100000", PATH + "&page=100001"), Arguments.of("size 不在允许值内", PATH + "&size=15"),
            Arguments.of("size 重复", PATH + "&size=10&size=20"), Arguments.of("未知参数", PATH + "&status=DRAFT"));
    }

    private FlowListResponseChecker list(String query) {
        return invoke(headers(), PATH + query);
    }

    private FlowListResponseChecker invoke(HttpHeaders headers, String uri) {
        String requestId = headers.getFirst(ApiHeaderNames.REQUEST_ID);
        return new FlowListResponseChecker(http.get(uri, headers), objectMapper, requestId);
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
        return "risk-flow-list-" + UUID.randomUUID();
    }
}

package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.*;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;

import java.util.HashMap;
import java.util.Map;
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
import com.paytech.risk.decision.api.DecisionConfigurationQueryApi;
import com.paytech.risk.test.base.RiskDecisionMockTestBase;
import com.paytech.risk.test.checker.decision.RuleSetHistoryResponseChecker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

/**
 * 规则集历史分页查询
 * <li>正常流程：固定业务版本上界内按版本号倒序分页，当前标记只取自根的当前指针，不受上界影响。</li>
 * <li>未找到：规则集不存在或整体删除返回 404。</li>
 * <li>参数：身份 Header、路径 ID、上界、分页、未知/重复/空 Query 与 GET Body 均在查询前拒绝（400）。</li>
 * <li>闭包损坏：版本内容摘要不符时失败关闭（500），不返回被篡改的历史。</li>
 *
 * @see DecisionConfigurationQueryApi#history(String, String, String, Integer, Integer, Integer)
 */
class RuleSetHistoryApiAutoTest extends RiskDecisionMockTestBase {
    private static final String PATH = "/decision-rule-sets/v1/rule-sets/%s/versions";

    @Autowired
    private RuleSetMocker ruleSets;

    /** 冻结、退役、草稿三个版本，上界为当前版本时全部按版本号倒序返回，仅当前草稿标记为当前。 */
    @Test
    void test_normalFlow() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, RETIRED, DRAFT));

        read(ruleSet, "maxVersionNo=3").assertPage(1, 10, 3).assertVersions(ruleSet, 3, 2, 1);
    }

    /** 上界低于当前版本时只返回上界内版本，且其中没有当前版本。 */
    @Test
    void test_normalFlow_upperBoundExcludesNewerVersions() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, RETIRED, DRAFT));

        read(ruleSet, "maxVersionNo=2").assertPage(1, 10, 2).assertVersions(ruleSet, 2, 1);
    }

    /** 当前版本为冻结版本，同样可作为历史上界。 */
    @Test
    void test_normalFlow_frozenCurrentVersion() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, FROZEN));

        read(ruleSet, "maxVersionNo=2").assertPage(1, 10, 2).assertVersions(ruleSet, 2, 1);
    }

    /** 分页按版本号倒序切分，越界页返回空列表但保留总数。 */
    @Test
    void test_normalFlow_paging() {
        MockedRuleSet ruleSet = ruleSets.mock(
            versions(FROZEN, RETIRED, RETIRED, RETIRED, RETIRED, RETIRED, RETIRED, RETIRED, RETIRED, RETIRED, DRAFT));

        read(ruleSet, "maxVersionNo=11&page=1&size=10").assertPage(1, 10, 11).assertVersions(ruleSet, 11, 10, 9, 8, 7,
            6, 5, 4, 3, 2);
        read(ruleSet, "maxVersionNo=11&page=2&size=10").assertPage(2, 10, 11).assertVersions(ruleSet, 1);
        read(ruleSet, "maxVersionNo=11&page=3&size=10").assertPage(3, 10, 11).assertVersions(ruleSet);
        read(ruleSet, "maxVersionNo=11&size=20").assertPage(1, 20, 11).assertVersions(ruleSet, 11, 10, 9, 8, 7, 6, 5, 4,
            3, 2, 1);
    }

    /** 同一决策点下的其他规则集版本不混入本规则集的历史。 */
    @Test
    void test_normalFlow_otherRuleSetsAreIsolated() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        ruleSets.mock(versions(FROZEN, FROZEN, DRAFT));

        read(ruleSet, "maxVersionNo=2").assertPage(1, 10, 2).assertVersions(ruleSet, 2, 1);
    }

    /** 规则集不存在或已整体删除。 */
    @Test
    void test_noDataFound() {
        MockedRuleSet deleted = ruleSets.mock(versions(FROZEN, DRAFT).deleted());

        read(deleted, "maxVersionNo=1").assertNotFound();
        // 越界空页不会加载任何版本详情，删除判定只能来自根本身。
        read(deleted, "maxVersionNo=1&page=2").assertNotFound();
        invoke(headers(), PATH.formatted("999999999999999999") + "?maxVersionNo=1").assertNotFound();
    }

    /** 当前页任一版本的内容摘要与内容不符时整体失败，不返回被篡改的历史。 */
    @Test
    void test_businessRuleViolation_corruptedVersionFailsClosed() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        ruleSets.corruptContentHash(ruleSet.version(1));

        read(ruleSet, "maxVersionNo=2").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 查询串非法时在查询前拒绝；规则集本身存在且合法。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, RETIRED, DRAFT));

        read(ruleSet, query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 路径中的规则集 ID 格式非法。 */
    @ParameterizedTest(name = "ruleSetId={0}")
    @MethodSource("invalidRuleSetIds")
    void test_invalidParameter_ruleSetId(String ruleSetId) {
        invoke(headers(), PATH.formatted(ruleSetId) + "?maxVersionNo=1").assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** Gateway 可信身份 Header 缺失或格式非法。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidHeaders")
    void test_invalidParameter_headers(String scenario, Map<String, String> overrides) {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        HttpHeaders headers = headers();
        overrides.forEach((name, value) -> {
            if (value == null) {
                headers.remove(name);
            } else {
                headers.set(name, value);
            }
        });

        invoke(headers, PATH.formatted(ruleSet.ruleSetId()) + "?maxVersionNo=1").assertProblem(400,
            INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        String requestId = nextRequestId();

        new RuleSetHistoryResponseChecker(http.exchange(HttpMethod.GET,
            PATH.formatted(ruleSet.ruleSetId()) + "?maxVersionNo=1", headers(requestId), "{}"), objectMapper, requestId)
            .assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("缺少 maxVersionNo", ""), Arguments.of("maxVersionNo 为 0", "maxVersionNo=0"),
            Arguments.of("maxVersionNo 为负数", "maxVersionNo=-1"), Arguments.of("maxVersionNo 超过当前版本", "maxVersionNo=4"),
            Arguments.of("maxVersionNo 非数字", "maxVersionNo=two"), Arguments.of("maxVersionNo 为空值", "maxVersionNo="),
            Arguments.of("maxVersionNo 重复", "maxVersionNo=1&maxVersionNo=1"),
            Arguments.of("page 为 0", "maxVersionNo=1&page=0"),
            Arguments.of("page 超过 100000", "maxVersionNo=1&page=100001"),
            Arguments.of("size 不在允许值内", "maxVersionNo=1&size=15"), Arguments.of("未知参数", "maxVersionNo=1&status=DRAFT"));
    }

    static Stream<String> invalidRuleSetIds() {
        return Stream.of("0", "-1", "abc", "012", "1".repeat(20), "9223372036854775808");
    }

    static Stream<Arguments> invalidHeaders() {
        return Stream.of(Arguments.of("缺少操作者", nullValue("X-Gateway-Actor-Subject")),
            Arguments.of("操作者非正整数", Map.of("X-Gateway-Actor-Subject", "0")),
            Arguments.of("操作者非数字", Map.of("X-Gateway-Actor-Subject", "admin")),
            Arguments.of("缺少会话", nullValue("X-Gateway-Session-Id")),
            Arguments.of("会话超过 128 字符", Map.of("X-Gateway-Session-Id", "s".repeat(129))),
            Arguments.of("会话含控制字符", Map.of("X-Gateway-Session-Id", "risk\tsession")));
    }

    /** 表示删除该 Header 的覆盖项。 */
    private static Map<String, String> nullValue(String name) {
        Map<String, String> map = new HashMap<>();
        map.put(name, null);
        return map;
    }

    private RuleSetHistoryResponseChecker read(MockedRuleSet ruleSet, String query) {
        String path = PATH.formatted(ruleSet.ruleSetId());
        return invoke(headers(), query.isEmpty() ? path : path + "?" + query);
    }

    private RuleSetHistoryResponseChecker invoke(HttpHeaders headers, String uri) {
        String requestId = headers.getFirst(ApiHeaderNames.REQUEST_ID);
        return new RuleSetHistoryResponseChecker(http.get(uri, headers), objectMapper, requestId);
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
        return "risk-rule-set-history-" + UUID.randomUUID();
    }
}

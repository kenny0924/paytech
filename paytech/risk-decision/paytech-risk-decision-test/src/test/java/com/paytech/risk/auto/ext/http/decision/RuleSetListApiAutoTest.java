package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.domain.enums.DecisionEnvironment.PRODUCTION;
import static com.paytech.risk.decision.domain.enums.DecisionEnvironment.TEST;
import static com.paytech.risk.decision.domain.enums.DecisionReleaseStatus.ACTIVE;
import static com.paytech.risk.decision.domain.enums.DecisionReleaseStatus.SUPERSEDED;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.*;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;

import java.time.Instant;
import java.util.ArrayList;
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

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.decision.api.DecisionRuleSetManagementApi;
import com.paytech.risk.test.base.RiskDecisionMockTestBase;
import com.paytech.risk.test.builder.decision.ReleaseBuilder;
import com.paytech.risk.test.checker.decision.RuleSetListResponseChecker;
import com.paytech.risk.test.checker.decision.RuleSetListResponseChecker.Item;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRelease;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

/**
 * 规则集配置头分页查询
 * <li>正常流程：每个未删除规则集一行（当前版本），按当前版本创建时间倒序分页；决策点、编码、状态筛选互相叠加。</li>
 * <li>使用状态：任一历史版本被 ACTIVE 发布占槽即 inUse；非活动发布只提供旧编排坐标，不算使用。</li>
 * <li>未找到：筛选无匹配时返回空页，整体删除的规则集不出现。</li>
 * <li>参数：分页、编码与状态筛选非法，以及未知、重复、空 Query 与 GET Body 在查询前拒绝（400）。</li>
 * <li>闭包损坏：当前指针不是最新版本、或同决策点活动发布引用损坏时失败关闭（500）。</li>
 *
 * @see DecisionRuleSetManagementApi#listRuleSets(String, String, Integer, Integer, String, String, String)
 */
class RuleSetListApiAutoTest extends RiskDecisionMockTestBase {
    private static final String PATH = "/decision-rule-sets/v1/rule-sets";

    private static final Instant T1 = Instant.parse("2026-03-01T00:00:00.000001Z");

    private static final Instant T2 = Instant.parse("2026-04-01T00:00:00.000001Z");

    private static final Instant T3 = Instant.parse("2026-05-01T00:00:00.000001Z");

    @Autowired
    private RuleSetMocker ruleSets;

    /** 三个规则集按当前版本创建时间倒序返回，未发布时 inUse 为 false 且无旧编排坐标。 */
    @Test
    void test_normalFlow() {
        MockedRuleSet oldest = ruleSets.mock(versions(FROZEN).createdFrom(T1));
        MockedRuleSet middle = ruleSets.mock(versions(FROZEN, DRAFT).createdFrom(T2));
        MockedRuleSet newest = ruleSets.mock(versions(DRAFT).createdFrom(T3));

        list("").assertPage(1, 10, 3).assertItems(Item.of(newest), Item.of(middle), Item.of(oldest));
    }

    /** 决策点、规则集编码与当前版本状态筛选，可叠加。 */
    @Test
    void test_normalFlow_filters() {
        MockedRuleSet frozen = ruleSets.mock(versions(FROZEN).createdFrom(T1));
        MockedRuleSet draftAfterFrozen = ruleSets.mock(versions(FROZEN, DRAFT).createdFrom(T2));
        MockedRuleSet draft = ruleSets.mock(versions(DRAFT).createdFrom(T3));

        list("pointCode=CHECKOUT_PREPARE").assertPage(1, 10, 3).assertItems(Item.of(draft),
            Item.of(draftAfterFrozen), Item.of(frozen));
        list("ruleSetCode=" + draftAfterFrozen.ruleSetCode()).assertPage(1, 10, 1)
            .assertItems(Item.of(draftAfterFrozen));
        list("status=DRAFT").assertPage(1, 10, 2).assertItems(Item.of(draft), Item.of(draftAfterFrozen));
        list("status=FROZEN").assertPage(1, 10, 1).assertItems(Item.of(frozen));
        // 当前版本只能是 DRAFT 或 FROZEN，历史 RETIRED 版本不会作为配置头出现。
        list("status=RETIRED").assertPage(1, 10, 0).assertItems();
        list("pointCode=CHECKOUT_PREPARE&ruleSetCode=" + frozen.ruleSetCode() + "&status=FROZEN").assertPage(1, 10, 1)
            .assertItems(Item.of(frozen));
        list("ruleSetCode=" + frozen.ruleSetCode() + "&status=DRAFT").assertPage(1, 10, 0).assertItems();
    }

    /** 按页切分，越界页为空但保留总数，size 取允许值。 */
    @Test
    void test_normalFlow_paging() {
        List<MockedRuleSet> newestFirst = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            newestFirst.addFirst(ruleSets.mock(versions(DRAFT).createdFrom(T1.plusSeconds(60L * i))));
        }
        Item[] all = newestFirst.stream().map(Item::of).toArray(Item[]::new);

        list("page=1&size=10").assertPage(1, 10, 11).assertItems(java.util.Arrays.copyOfRange(all, 0, 10));
        list("page=2&size=10").assertPage(2, 10, 11).assertItems(all[10]);
        list("page=3&size=10").assertPage(3, 10, 11).assertItems();
        list("size=20").assertPage(1, 20, 11).assertItems(all);
        list("page=100000&size=200").assertPage(100000, 200, 11).assertItems();
    }

    /** ACTIVE 发布引用旧冻结版本即 inUse；仅 SUPERSEDED 发布引用当前版本时只返回旧编排坐标。 */
    @Test
    void test_normalFlow_usageAndCompanion() {
        MockedRuleSet used = ruleSets.mock(versions(FROZEN, DRAFT).createdFrom(T2));
        ruleSets.mockRelease(used, 1, ReleaseBuilder.of(TEST, ACTIVE));
        MockedRuleSet superseded = ruleSets.mock(versions(FROZEN).createdFrom(T1));
        MockedRelease history = ruleSets.mockRelease(superseded, 1, ReleaseBuilder.of(PRODUCTION, SUPERSEDED));

        list("").assertPage(1, 10, 2).assertItems(new Item(used, null, true),
            new Item(superseded, history.pointVersionId(), false));
    }

    /** 整体删除的规则集不再作为配置头出现，也不计入总数。 */
    @Test
    void test_noDataFound_deletedExcluded() {
        ruleSets.mock(versions(FROZEN, DRAFT).deleted().createdFrom(T2));
        MockedRuleSet kept = ruleSets.mock(versions(DRAFT).createdFrom(T1));

        list("").assertPage(1, 10, 1).assertItems(Item.of(kept));
    }

    /** 筛选无匹配时返回空页。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("unmatchedFilters")
    void test_noDataFound(String scenario, String query) {
        ruleSets.mock(versions(FROZEN, DRAFT));

        list(query).assertPage(1, 10, 0).assertItems();
    }

    /** 分页、编码与状态筛选非法。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        ruleSets.mock(versions(DRAFT));

        list(query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        ruleSets.mock(versions(DRAFT));
        String requestId = "risk-rule-set-list-" + UUID.randomUUID();

        new RuleSetListResponseChecker(http.exchange(HttpMethod.GET, PATH, headers(requestId), "{}"), objectMapper,
            requestId).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 根的当前指针不是最新版本时整体失败，不静默显示旧版本。 */
    @Test
    void test_businessRuleViolation_staleHeadFailsClosed() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        ruleSets.corruptCurrentVersion(ruleSet, 1);

        list("").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 同决策点的 ACTIVE 发布引用了未冻结版本，使用状态无法可信判断时整体失败。 */
    @Test
    void test_businessRuleViolation_corruptActiveReleaseFailsClosed() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        ruleSets.mockRelease(ruleSet, 2, ReleaseBuilder.of(TEST, ACTIVE));

        list("").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    static Stream<Arguments> unmatchedFilters() {
        return Stream.of(Arguments.of("未知规则集编码", "ruleSetCode=MOCK_RULE_SET_UNKNOWN"),
            Arguments.of("未知决策点编码", "pointCode=PAYMENT_AUTHORIZE"),
            Arguments.of("状态不匹配", "status=FROZEN"));
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("page 为 0", "page=0"), Arguments.of("page 超过 100000", "page=100001"),
            Arguments.of("page 为负数", "page=-1"), Arguments.of("page 非数字", "page=one"),
            Arguments.of("size 不在允许值内", "size=15"), Arguments.of("size 为 0", "size=0"),
            Arguments.of("size 非数字", "size=ten"), Arguments.of("pointCode 小写", "pointCode=checkout_prepare"),
            Arguments.of("pointCode 为空", "pointCode="), Arguments.of("ruleSetCode 含连字符", "ruleSetCode=RULE-SET"),
            Arguments.of("ruleSetCode 超过 64 位", "ruleSetCode=" + "R".repeat(65)),
            Arguments.of("status 小写", "status=draft"), Arguments.of("status 为管理状态", "status=DELETED"),
            Arguments.of("status 未知", "status=ACTIVE"), Arguments.of("未知参数", "keyword=RULE"),
            Arguments.of("page 为空值", "page="), Arguments.of("size 为空值", "size="),
            Arguments.of("page 重复", "page=1&page=1"));
    }

    private RuleSetListResponseChecker list(String query) {
        String requestId = "risk-rule-set-list-" + UUID.randomUUID();
        return new RuleSetListResponseChecker(http.get(query.isEmpty() ? PATH : PATH + "?" + query,
            headers(requestId)), objectMapper, requestId);
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }
}

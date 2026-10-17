package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.domain.enums.DecisionEnvironment.PRODUCTION;
import static com.paytech.risk.decision.domain.enums.DecisionEnvironment.TEST;
import static com.paytech.risk.decision.domain.enums.DecisionReleaseStatus.ACTIVE;
import static com.paytech.risk.decision.domain.enums.DecisionReleaseStatus.APPROVED;
import static com.paytech.risk.decision.domain.enums.DecisionReleaseStatus.REJECTED;
import static com.paytech.risk.decision.domain.enums.DecisionReleaseStatus.SUPERSEDED;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.DRAFT;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.FROZEN;
import static com.paytech.risk.test.builder.decision.ReleaseBuilder.of;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.api.decision.DecisionConfigurationQueryApi;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.checker.decision.RuleSetReleaseReferencesResponseChecker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRelease;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

/**
 * 规则集发布引用查询
 * <li>正常流程：引用按发布 ID 倒序分页；inUse 只看全根是否存在 ACTIVE 发布，不受筛选与分页影响。</li>
 * <li>筛选：环境、状态与精确版本组合筛选；精确版本必须属于本规则集。</li>
 * <li>未找到：规则集不存在、已整体删除、或精确版本不存在/不属于本规则集返回 404。</li>
 * <li>闭包损坏：同一决策点任一发布断边时失败关闭（500），不返回空引用。</li>
 *
 * @see DecisionConfigurationQueryApi#releases(String, String, String, Integer, Integer, String, String, String)
 */
class RuleSetReleaseReferencesApiAutoTest extends MockTestBase {
    private static final String PATH = "/decision-rule-sets/v1/rule-sets/%s/release-references";

    @Autowired
    private RuleSetMocker ruleSets;

    /** 两个冻结版本在两个环境的多次发布：全部引用按发布 ID 倒序返回，可空时间按状态输出。 */
    @Test
    void test_normalFlow() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, FROZEN, DRAFT));
        MockedRelease superseded = ruleSets.mockRelease(ruleSet, 1, of(PRODUCTION, SUPERSEDED));
        MockedRelease active = ruleSets.mockRelease(ruleSet, 1, of(TEST, ACTIVE));
        MockedRelease approved = ruleSets.mockRelease(ruleSet, 2, of(PRODUCTION, APPROVED));

        read(ruleSet, "").assertUsage(ruleSet, true).assertPage(1, 10, 3).assertReferences(approved, active,
            superseded);
    }

    /** 只有非生效发布时整体未被使用；没有任何发布时返回空页。 */
    @Test
    void test_normalFlow_notInUse() {
        MockedRuleSet released = ruleSets.mock(versions(FROZEN, DRAFT));
        MockedRelease rejected = ruleSets.mockRelease(released, 1, of(TEST, REJECTED));
        MockedRuleSet unreleased = ruleSets.mock(versions(DRAFT));

        read(released, "").assertUsage(released, false).assertPage(1, 10, 1).assertReferences(rejected);
        read(unreleased, "").assertUsage(unreleased, false).assertPage(1, 10, 0).assertReferences();
    }

    /** 旧冻结版本在任一环境生效时，筛选为空或越界页仍报告整体 inUse。 */
    @ParameterizedTest(name = "{0}")
    @EnumSource(DecisionEnvironment.class)
    void test_normalFlow_inUseIgnoresFiltersAndPaging(DecisionEnvironment environment) {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        ruleSets.mockRelease(ruleSet, 1, of(environment, ACTIVE));

        read(ruleSet, "status=DRAFT").assertUsage(ruleSet, true).assertPage(1, 10, 0).assertReferences();
        read(ruleSet, "page=2").assertUsage(ruleSet, true).assertPage(2, 10, 1).assertReferences();
        read(ruleSet, "ruleSetVersionId=" + ruleSet.version(2).ruleSetVersionId()).assertUsage(ruleSet, true)
            .assertPage(1, 10, 0).assertReferences();
    }

    /** 环境、状态、精确版本单独及组合筛选只返回匹配的引用，总数同步收窄。 */
    @Test
    void test_normalFlow_filters() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, FROZEN, DRAFT));
        MockedRelease v1Prod = ruleSets.mockRelease(ruleSet, 1, of(PRODUCTION, SUPERSEDED));
        MockedRelease v1Test = ruleSets.mockRelease(ruleSet, 1, of(TEST, ACTIVE));
        MockedRelease v2Prod = ruleSets.mockRelease(ruleSet, 2, of(PRODUCTION, ACTIVE));
        MockedRelease v2Test = ruleSets.mockRelease(ruleSet, 2, of(TEST, REJECTED));
        String v1 = Long.toString(ruleSet.version(1).ruleSetVersionId());

        read(ruleSet, "environmentCode=PRODUCTION").assertPage(1, 10, 2).assertReferences(v2Prod, v1Prod);
        read(ruleSet, "status=ACTIVE").assertPage(1, 10, 2).assertReferences(v2Prod, v1Test);
        read(ruleSet, "ruleSetVersionId=" + v1).assertPage(1, 10, 2).assertReferences(v1Test, v1Prod);
        read(ruleSet, "environmentCode=TEST&status=REJECTED").assertPage(1, 10, 1).assertReferences(v2Test);
        read(ruleSet, "environmentCode=TEST&ruleSetVersionId=" + v1).assertPage(1, 10, 1).assertReferences(v1Test);
    }

    /** 分页按发布 ID 倒序切分。 */
    @Test
    void test_normalFlow_paging() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        MockedRelease[] releases = new MockedRelease[11];
        for (int i = 0; i < releases.length; i++) {
            releases[i] = ruleSets.mockRelease(ruleSet, 1, of(i % 2 == 0 ? TEST : PRODUCTION, REJECTED));
        }

        read(ruleSet, "page=1&size=10").assertPage(1, 10, 11).assertReferences(releases[10], releases[9], releases[8],
            releases[7], releases[6], releases[5], releases[4], releases[3], releases[2], releases[1]);
        read(ruleSet, "page=2&size=10").assertPage(2, 10, 11).assertReferences(releases[0]);
    }

    /** 同一决策点下其他规则集的发布既不计入引用，也不使本规则集 inUse。 */
    @Test
    void test_normalFlow_otherRuleSetsAreIsolated() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        MockedRelease own = ruleSets.mockRelease(ruleSet, 1, of(TEST, REJECTED));
        MockedRuleSet other = ruleSets.mock(versions(FROZEN, DRAFT));
        ruleSets.mockRelease(other, 1, of(PRODUCTION, ACTIVE));

        read(ruleSet, "").assertUsage(ruleSet, false).assertPage(1, 10, 1).assertReferences(own);
    }

    /** 规则集不存在、已整体删除，或精确版本不存在或属于其他规则集。 */
    @Test
    void test_noDataFound() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        MockedRuleSet other = ruleSets.mock(versions(FROZEN, DRAFT));
        MockedRuleSet deleted = ruleSets.mock(versions(FROZEN, DRAFT).deleted());

        read(deleted, "").assertNotFound();
        invoke(headers(), PATH.formatted("999999999999999999")).assertNotFound();
        read(ruleSet, "ruleSetVersionId=" + other.version(1).ruleSetVersionId()).assertNotFound();
        read(ruleSet, "ruleSetVersionId=999999999999999999").assertNotFound();
    }

    /** 同一决策点上任一发布断边时，即使不落入当前筛选也失败关闭。 */
    @Test
    void test_businessRuleViolation_brokenReleaseFailsClosed() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        ruleSets.mockRelease(ruleSet, 1, of(PRODUCTION, ACTIVE));
        ruleSets.mockBrokenRelease();

        read(ruleSet, "status=ACTIVE").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 筛选、分页或 Query 形状非法时在查询前拒绝；规则集本身存在且合法。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        ruleSets.mockRelease(ruleSet, 1, of(TEST, ACTIVE));

        read(ruleSet, query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 路径中的规则集 ID 格式非法。 */
    @ParameterizedTest(name = "ruleSetId={0}")
    @ValueSource(strings = {"0", "-1", "abc", "012", "11111111111111111111", "9223372036854775808"})
    void test_invalidParameter_ruleSetId(String ruleSetId) {
        invoke(headers(), PATH.formatted(ruleSetId)).assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("环境未知", "environmentCode=UAT"), Arguments.of("环境小写", "environmentCode=test"),
            Arguments.of("环境为空值", "environmentCode="), Arguments.of("状态未知", "status=LIVE"),
            Arguments.of("状态小写", "status=active"), Arguments.of("版本 ID 为 0", "ruleSetVersionId=0"),
            Arguments.of("版本 ID 非数字", "ruleSetVersionId=abc"),
            Arguments.of("版本 ID 超出 signed BIGINT", "ruleSetVersionId=9223372036854775808"),
            Arguments.of("page 为 0", "page=0"), Arguments.of("size 不在允许值内", "size=15"),
            Arguments.of("重复筛选", "status=ACTIVE&status=ACTIVE"), Arguments.of("历史接口的参数", "maxVersionNo=1"),
            Arguments.of("未知参数", "keyword=x"));
    }

    private RuleSetReleaseReferencesResponseChecker read(MockedRuleSet ruleSet, String query) {
        String path = PATH.formatted(ruleSet.ruleSetId());
        return invoke(headers(), query.isEmpty() ? path : path + "?" + query);
    }

    private RuleSetReleaseReferencesResponseChecker invoke(HttpHeaders headers, String uri) {
        return new RuleSetReleaseReferencesResponseChecker(http.get(uri, headers), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    /** Gateway 转发的 Operations 可信身份；Header 校验场景见历史查询测试，两者共用同一解析器。 */
    private static HttpHeaders headers() {
        HttpHeaders headers = TestRequestHeaders.internal("risk-rule-set-releases-" + UUID.randomUUID());
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }
}

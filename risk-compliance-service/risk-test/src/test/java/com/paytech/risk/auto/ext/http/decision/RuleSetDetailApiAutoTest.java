package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.domain.enums.DecisionEnvironment.PRODUCTION;
import static com.paytech.risk.decision.domain.enums.DecisionReleaseStatus.SUPERSEDED;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.*;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.decision.DecisionRuleSetManagementApi;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.builder.decision.ReleaseBuilder;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.checker.decision.RuleSetDetailResponseChecker;
import com.paytech.risk.test.checker.decision.RuleSetStoreChecker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRelease;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

/**
 * 规则集准确版本详情查询
 * <li>正常流程：任一历史版本（冻结、退役、当前草稿）按原内容还原规则；被发布引用的冻结版本带旧编排坐标。</li>
 * <li>统一变量格式：附带被引用变量的当前展示值与读取时间；旧格式不引用变量。</li>
 * <li>未找到：版本不存在，或所属规则集已整体删除（含历史版本）时返回 404。</li>
 * <li>参数：路径版本 ID 不是正 BIGINT 十进制文本时在查询前拒绝（400）。</li>
 * <li>闭包损坏：内容摘要与规则闭包不符时失败关闭（500）。</li>
 *
 * @see DecisionRuleSetManagementApi#getRuleSet(String, String, String)
 */
class RuleSetDetailApiAutoTest extends MockTestBase {
    private static final String PATH = "/decision-rule-sets/v1/rule-sets/%s";

    @Autowired
    private RuleSetMocker ruleSets;

    @Autowired
    private RuleSetStoreChecker store;

    /** 冻结的历史版本按原内容返回冻结证据，旧格式没有当前变量。 */
    @Test
    void test_normalFlow() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));

        read(ruleSet, 1).assertLegacyVersion(ruleSet, 1, null);
    }

    /** 当前草稿与已退役版本同样可按准确版本读取。 */
    @Test
    void test_normalFlow_draftAndRetired() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, RETIRED, DRAFT));

        read(ruleSet, 3).assertLegacyVersion(ruleSet, 3, null);
        read(ruleSet, 2).assertLegacyVersion(ruleSet, 2, null);
    }

    /** 被历史发布引用的冻结版本返回其旧编排决策点版本 ID。 */
    @Test
    void test_normalFlow_releasedVersionHasCompanion() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN));
        MockedRelease release = ruleSets.mockRelease(ruleSet, 1, ReleaseBuilder.of(PRODUCTION, SUPERSEDED));

        read(ruleSet, 1).assertLegacyVersion(ruleSet, 1, release.pointVersionId());
    }

    /** 统一变量格式附带被引用变量的当前定义投影。 */
    @Test
    void test_normalFlow_variableReference() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT).variableReference());

        read(ruleSet, 1).assertVariableVersion(ruleSet, 1, store.variable(RuleSetBuilder.COUNTRY_VARIABLE));
    }

    /** 版本不存在，或规则集已整体删除时其当前与历史版本都不可读。 */
    @Test
    void test_noDataFound() {
        MockedRuleSet deleted = ruleSets.mock(versions(FROZEN, DRAFT).deleted());

        read(deleted, 2).assertNotFound();
        read(deleted, 1).assertNotFound();
        read("999999999999999999").assertNotFound();
    }

    /** 路径中的版本 ID 格式非法。 */
    @ParameterizedTest(name = "ruleSetVersionId={0}")
    @MethodSource("invalidVersionIds")
    void test_invalidParameter(String ruleSetVersionId) {
        read(ruleSetVersionId).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 版本内容摘要与规则闭包不符时失败关闭，不返回被篡改的内容。 */
    @Test
    void test_businessRuleViolation_corruptedVersionFailsClosed() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        ruleSets.corruptContentHash(ruleSet.version(1));

        read(ruleSet, 1).assertProblem(500, INTERNAL_ERROR.getCode());
        // 损坏只影响该版本，其他版本仍可读取。
        read(ruleSet, 2).assertLegacyVersion(ruleSet, 2, null);
    }

    static Stream<String> invalidVersionIds() {
        return Stream.of("0", "-1", "abc", "012", "1.5", "1".repeat(20), "9223372036854775808");
    }

    private RuleSetDetailResponseChecker read(MockedRuleSet ruleSet, int versionNo) {
        return read(Long.toString(ruleSet.version(versionNo).ruleSetVersionId()));
    }

    private RuleSetDetailResponseChecker read(String ruleSetVersionId) {
        String requestId = "risk-rule-set-detail-" + UUID.randomUUID();
        Instant before = Instant.now();
        var result = http.get(PATH.formatted(ruleSetVersionId), headers(requestId));
        return new RuleSetDetailResponseChecker(result, objectMapper, requestId, before, Instant.now());
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }
}

package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.BUSINESS_CHECK_FAILED;
import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND;
import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.RISK_RULE_SET_DELETED;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.*;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.decision.DecisionRuleSetManagementApi;
import com.paytech.risk.api.decision.request.FreezeRuleSetVersionRequestBody;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.checker.decision.RuleSetCommandResponseChecker;
import com.paytech.risk.test.checker.decision.RuleSetStoreChecker;
import com.paytech.risk.test.checker.decision.RuleSetStoreChecker.Snapshot;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedVersion;

/**
 * 冻结当前 DRAFT 规则版本
 * <li>正常流程：内容摘要一致的当前旧格式草稿变为 FROZEN 并写入冻结证据，管理根版本递增；更早版本不变。</li>
 * <li>未找到：版本不存在返回 404。</li>
 * <li>参数：路径版本 ID、expectedContentHash 的缺失、格式与报文形状非法时拒绝（400/422/415）。</li>
 * <li>运行期：结账已接入运行期，当前合同的统一变量草稿同样可以冻结。</li>
 * <li>业务规则：摘要不符或目标不是当前版本返回并发冲突（409）；已冻结不可再冻结（400）；已删除规则集（409）；资源锁被占用（409）。</li>
 * <li>失败关闭：内容摘要与闭包不符、动作目录漂移、管理根指针损坏时返回 500。所有拒绝都不改写持久化事实。</li>
 *
 * @see DecisionRuleSetManagementApi#freezeRuleSetVersion(String, String, String, FreezeRuleSetVersionRequestBody)
 */
class RuleSetFreezeApiAutoTest extends MockTestBase {
    private static final String PATH = "/decision-rule-sets/v1/rule-sets/%s/freeze";

    private static final long ACTOR = 70001L;

    @Autowired
    private RuleSetMocker ruleSets;

    @Autowired
    private RuleSetStoreChecker store;

    /** 冻结当前草稿：版本写入冻结证据，管理根版本递增，历史冻结版本不变。 */
    @Test
    void test_normalFlow() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        RuleSetCommandResponseChecker response = freeze(ruleSet.version(2), ruleSet.version(2).contentHash())
            .assertFrozen(ruleSet.ruleSetCode(), ruleSet.version(2).ruleSetVersionId(), 2,
                ruleSet.version(2).contentHash(), ACTOR);

        assertFrozen(before, ruleSet, 2, response.commandTime());
        store.assertVersionChanged(before, store.snapshot(ruleSet.ruleSetCode()), 1, Map.of());
    }

    /** 唯一的首个草稿同样可以冻结。 */
    @Test
    void test_normalFlow_firstDraft() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        RuleSetCommandResponseChecker response = freeze(ruleSet.version(1), ruleSet.version(1).contentHash())
            .assertFrozen(ruleSet.ruleSetCode(), ruleSet.version(1).ruleSetVersionId(), 1,
                ruleSet.version(1).contentHash(), ACTOR);

        assertFrozen(before, ruleSet, 1, response.commandTime());
    }

    /** 版本不存在。 */
    @Test
    void test_noDataFound() {
        invoke("999999999999999999", body("a".repeat(64))).assertProblem(404,
            RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }

    /** 路径中的版本 ID 格式非法。 */
    @ParameterizedTest(name = "ruleSetVersionId={0}")
    @MethodSource("invalidVersionIds")
    void test_invalidParameter_ruleSetVersionId(String ruleSetVersionId) {
        invoke(ruleSetVersionId, body("a".repeat(64))).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 内容摘要字段格式非法，由 Bean Validation 定位字段。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidHashes")
    void test_invalidParameter(String scenario, String requestBody) {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        invoke(id(ruleSet.version(1)), requestBody).assertProblem(422, VALIDATION_ERROR.getCode())
            .assertFieldError("expectedContentHash");

        store.assertUnchanged(before);
    }

    /** 无法绑定到请求合同的报文。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedBodies")
    void test_invalidParameter_malformedBody(String scenario, String requestBody) {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        invoke(id(ruleSet.version(1)), requestBody).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertUnchanged(before);
    }

    /** 非 JSON 请求在进入合同前以 415 拒绝。 */
    @Test
    void test_invalidParameter_unsupportedMediaType() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        invoke(id(ruleSet.version(1)), body(ruleSet.version(1).contentHash()), MediaType.TEXT_PLAIN)
            .assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());

        store.assertUnchanged(before);
    }

    /** 调用方读取的内容摘要已过期，或目标已不是当前版本。 */
    @Test
    void test_businessRuleViolation_concurrentChange() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        freeze(ruleSet.version(2), "f".repeat(64)).assertProblem(409, CONCURRENT_REQUEST.getCode());
        freeze(ruleSet.version(1), ruleSet.version(1).contentHash()).assertProblem(409, CONCURRENT_REQUEST.getCode());

        store.assertUnchanged(before);
    }

    /** 结账运行期：当前合同的统一变量草稿可以冻结，供执行发布引用。 */
    @Test
    void test_normalFlow_variableReference() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT).variableReference());
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        RuleSetCommandResponseChecker response = freeze(ruleSet.version(1), ruleSet.version(1).contentHash())
            .assertFrozen(ruleSet.ruleSetCode(), ruleSet.version(1).ruleSetVersionId(), 1,
                ruleSet.version(1).contentHash(), ACTOR);

        assertFrozen(before, ruleSet, 1, response.commandTime());
    }

    /** 当前版本已冻结时不可再冻结。 */
    @Test
    void test_businessRuleViolation_notFreezable() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        freeze(ruleSet.current(), ruleSet.current().contentHash()).assertProblem(400,
            BUSINESS_CHECK_FAILED.getCode());

        store.assertUnchanged(before);
    }

    /** 已整体删除的规则集不可冻结。 */
    @Test
    void test_businessRuleViolation_deletedRuleSet() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT).deleted());
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        freeze(ruleSet.version(2), ruleSet.version(2).contentHash()).assertProblem(409,
            RISK_RULE_SET_DELETED.getCode());

        store.assertUnchanged(before);
    }

    /** 同一规则集资源锁被其他命令占用时快速失败。 */
    @Test
    void test_businessRuleViolation_resourceLocked() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());
        ruleSets.holdResourceLock(ruleSet.ruleSetCode());

        freeze(ruleSet.version(1), ruleSet.version(1).contentHash()).assertProblem(409,
            CONCURRENT_REQUEST.getCode());

        store.assertUnchanged(before);
    }

    /** 闭包损坏、动作目录漂移或管理根指针损坏时失败关闭。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("corruptions")
    void test_businessRuleViolation_failsClosed(String scenario, String corruption) {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        String expectedHash = ruleSet.version(2).contentHash();
        switch (corruption) {
            case "hash" -> {
                ruleSets.corruptContentHash(ruleSet.version(2));
                expectedHash = "0".repeat(64);
            }
            case "catalog" -> ruleSets.driftActionCatalog();
            default -> ruleSets.corruptCurrentVersion(ruleSet, 1);
        }
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        freeze(ruleSet.version(2), expectedHash).assertProblem(500, INTERNAL_ERROR.getCode());

        store.assertUnchanged(before);
    }

    static Stream<String> invalidVersionIds() {
        return Stream.of("0", "-1", "abc", "012", "1".repeat(20), "9223372036854775808");
    }

    static Stream<Arguments> invalidHashes() {
        return Stream.of(Arguments.of("大写", body("A".repeat(64))), Arguments.of("63 位", body("a".repeat(63))),
            Arguments.of("65 位", body("a".repeat(65))), Arguments.of("非十六进制", body("g".repeat(64))),
            Arguments.of("显式 null", "{\"expectedContentHash\":null}"));
    }

    static Stream<Arguments> malformedBodies() {
        return Stream.of(Arguments.of("缺少 expectedContentHash", "{}"), Arguments.of("非法 JSON", "{\"expected"),
            Arguments.of("空报文", ""), Arguments.of("顶层为数组", "[]"),
            Arguments.of("未知字段", "{\"expectedContentHash\":\"" + "a".repeat(64) + "\",\"status\":\"FROZEN\"}"));
    }

    static Stream<Arguments> corruptions() {
        return Stream.of(Arguments.of("内容摘要与闭包不符", "hash"), Arguments.of("动作目录漂移", "catalog"),
            Arguments.of("管理根指针不是最新版本", "head"));
    }

    /** 目标版本写入冻结证据，管理根仅版本与更新时间变化。 */
    private void assertFrozen(Snapshot before, MockedRuleSet ruleSet, int versionNo, Instant time) {
        Snapshot after = store.snapshot(ruleSet.ruleSetCode());
        store.assertVersionChanged(before, after, versionNo,
            Map.of("status", FROZEN.getCode(), "frozen_by", ACTOR, "frozen_time", time, "update_time", time));
        store.assertRootChanged(before, after, Map.of("version", 2L, "update_time", time));
    }

    private static String body(String expectedContentHash) {
        return "{\"expectedContentHash\":\"" + expectedContentHash + "\"}";
    }

    private static String id(MockedVersion version) {
        return Long.toString(version.ruleSetVersionId());
    }

    private RuleSetCommandResponseChecker freeze(MockedVersion version, String expectedContentHash) {
        return invoke(id(version), body(expectedContentHash));
    }

    private RuleSetCommandResponseChecker invoke(String ruleSetVersionId, String requestBody) {
        return invoke(ruleSetVersionId, requestBody, MediaType.APPLICATION_JSON);
    }

    private RuleSetCommandResponseChecker invoke(String ruleSetVersionId, String requestBody, MediaType contentType) {
        String requestId = "risk-rule-set-freeze-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACTOR));
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        headers.setContentType(contentType);
        Instant before = Instant.now();
        var result = http.post(PATH.formatted(ruleSetVersionId), headers,
            requestBody.getBytes(StandardCharsets.UTF_8));
        return new RuleSetCommandResponseChecker(result, objectMapper, requestId, before, Instant.now());
    }
}

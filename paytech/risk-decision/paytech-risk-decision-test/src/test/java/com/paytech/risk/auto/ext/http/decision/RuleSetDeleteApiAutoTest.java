package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.api.enums.RiskDecisionConfigurationErrorCode.*;
import static com.paytech.risk.decision.domain.enums.DecisionEnvironment.PRODUCTION;
import static com.paytech.risk.decision.domain.enums.DecisionEnvironment.TEST;
import static com.paytech.risk.decision.domain.enums.DecisionReleaseStatus.ACTIVE;
import static com.paytech.risk.decision.domain.enums.DecisionReleaseStatus.DRAFT;
import static com.paytech.risk.decision.domain.enums.DecisionReleaseStatus.SUPERSEDED;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.FROZEN;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.decision.DecisionRuleSetManagementApi;
import com.paytech.risk.api.decision.request.DeleteRuleSetRequestBody;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.builder.decision.ReleaseBuilder;
import com.paytech.risk.test.builder.decision.RuleSetBuilder;
import com.paytech.risk.test.checker.decision.RuleSetCommandResponseChecker;
import com.paytech.risk.test.checker.decision.RuleSetStoreChecker;
import com.paytech.risk.test.checker.decision.RuleSetStoreChecker.Snapshot;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

/**
 * 整体删除未被活动发布使用的规则集
 * <li>正常流程：按准确管理版本删除，只改变管理根（DELETED、版本递增、删除证据），全部历史版本与规则闭包保留；非活动发布不阻止删除。</li>
 * <li>未找到：规则集整体 ID 不存在返回 404。</li>
 * <li>参数：路径 ID、expectedVersion 必须为唯一字符串字段且为正 BIGINT 十进制文本，报文形状、大小与媒体类型非法时拒绝（400/413/422）。</li>
 * <li>业务规则：管理版本过期（409 CHANGED）、已删除（409 DELETED）、任一版本被 ACTIVE 发布使用（409 IN_USE）、资源锁被占用（409）。</li>
 * <li>失败关闭：同决策点活动发布引用损坏、管理根指针损坏时返回 500。所有拒绝都不改写持久化事实。</li>
 *
 * @see DecisionRuleSetManagementApi#deleteRuleSet(String, String, String, DeleteRuleSetRequestBody)
 */
class RuleSetDeleteApiAutoTest extends MockTestBase {
    private static final String PATH = "/decision-rule-sets/v1/rule-sets/%s";

    private static final long ACTOR = 70001L;

    @Autowired
    private RuleSetMocker ruleSets;

    @Autowired
    private RuleSetStoreChecker store;

    /** 删除只改变管理根并写入删除证据，冻结与草稿历史版本完整保留。 */
    @Test
    void test_normalFlow() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, RuleSetVersionStatus.DRAFT));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        RuleSetCommandResponseChecker response = delete(ruleSet, "1").assertDeleted(ruleSet.ruleSetId(), 2, ACTOR);

        assertDeleted(before, ruleSet, response.commandTime());
    }

    /** 历史版本只被非活动发布引用（已替代、草稿发布）时不算使用，可以删除。 */
    @Test
    void test_normalFlow_inactiveReleasesDoNotBlock() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, RuleSetVersionStatus.DRAFT));
        ruleSets.mockRelease(ruleSet, 1, ReleaseBuilder.of(PRODUCTION, SUPERSEDED));
        ruleSets.mockRelease(ruleSet, 1, ReleaseBuilder.of(TEST, DRAFT));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        RuleSetCommandResponseChecker response = delete(ruleSet, "1").assertDeleted(ruleSet.ruleSetId(), 2, ACTOR);

        assertDeleted(before, ruleSet, response.commandTime());
    }

    /** 同决策点其他规则集被活动发布使用，不影响本规则集删除。 */
    @Test
    void test_normalFlow_otherRuleSetInUse() {
        MockedRuleSet used = ruleSets.mock(versions(FROZEN));
        ruleSets.mockRelease(used, 1, ReleaseBuilder.of(TEST, ACTIVE));
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());
        Snapshot usedBefore = store.snapshot(used.ruleSetCode());

        RuleSetCommandResponseChecker response = delete(ruleSet, "1").assertDeleted(ruleSet.ruleSetId(), 2, ACTOR);

        assertDeleted(before, ruleSet, response.commandTime());
        store.assertUnchanged(usedBefore);
    }

    /** 规则集整体 ID 不存在。 */
    @Test
    void test_noDataFound() {
        invoke("999999999999999999", body("1")).assertProblem(404, RISK_DECISION_CONFIGURATION_NOT_FOUND.getCode());
    }

    /** 路径中的规则集 ID 格式非法。 */
    @ParameterizedTest(name = "ruleSetId={0}")
    @MethodSource("invalidRuleSetIds")
    void test_invalidParameter_ruleSetId(String ruleSetId) {
        invoke(ruleSetId, body("1")).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** expectedVersion 字符串不是正 BIGINT 十进制文本，由 Bean Validation 定位字段。 */
    @ParameterizedTest(name = "expectedVersion={0}")
    @MethodSource("invalidVersions")
    void test_invalidParameter(String expectedVersion) {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        delete(ruleSet, expectedVersion).assertProblem(422, VALIDATION_ERROR.getCode())
            .assertFieldError("expectedVersion");

        store.assertUnchanged(before);
    }

    /** 报文不是只含字符串 expectedVersion 的唯一 JSON 对象。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedBodies")
    void test_invalidParameter_malformedBody(String scenario, String requestBody) {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        invoke(id(ruleSet), requestBody).assertProblem(400, INVALID_REQUEST.getCode());

        store.assertUnchanged(before);
    }

    /** 非 JSON 的 DELETE 报文不被读取，按缺少请求体拒绝（400）；超过 64 KiB 的报文以 413 拒绝。 */
    @Test
    void test_invalidParameter_mediaTypeAndSize() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        invoke(id(ruleSet), body("1"), MediaType.TEXT_PLAIN).assertProblem(400, INVALID_REQUEST.getCode());
        invoke(id(ruleSet), "{\"expectedVersion\":\"1\"," + " ".repeat(70_000) + "}", MediaType.APPLICATION_JSON)
            .assertProblem(413, INVALID_REQUEST.getCode());

        store.assertUnchanged(before);
    }

    /** 调用方确认的管理版本已过期。 */
    @Test
    void test_businessRuleViolation_versionChanged() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        delete(ruleSet, "2").assertProblem(409, RISK_RULE_SET_CHANGED.getCode());

        store.assertUnchanged(before);
    }

    /** 已整体删除的规则集再次删除，原删除证据不变。 */
    @Test
    void test_businessRuleViolation_alreadyDeleted() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN).deleted());
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        delete(ruleSet, "2").assertProblem(409, RISK_RULE_SET_DELETED.getCode());

        store.assertUnchanged(before);
    }

    /** 任一环境的 ACTIVE 发布仍使用任一历史版本时，最新草稿也不可删除。 */
    @ParameterizedTest(name = "environment={0}")
    @EnumSource(DecisionEnvironment.class)
    void test_businessRuleViolation_inUse(DecisionEnvironment environment) {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, RuleSetVersionStatus.DRAFT));
        ruleSets.mockRelease(ruleSet, 1, ReleaseBuilder.of(environment, ACTIVE));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        delete(ruleSet, "1").assertProblem(409, RISK_RULE_SET_IN_USE.getCode());

        store.assertUnchanged(before);
    }

    /** 同一规则集资源锁被其他命令占用时快速失败。 */
    @Test
    void test_businessRuleViolation_resourceLocked() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());
        ruleSets.holdResourceLock(ruleSet.ruleSetCode());

        delete(ruleSet, "1").assertProblem(409, CONCURRENT_REQUEST.getCode());

        store.assertUnchanged(before);
    }

    /** 同决策点 ACTIVE 发布引用了未冻结版本，使用状态无法可信判断时失败关闭。 */
    @Test
    void test_businessRuleViolation_corruptActiveReleaseFailsClosed() {
        MockedRuleSet broken = ruleSets.mock(versions(RuleSetVersionStatus.DRAFT));
        ruleSets.mockRelease(broken, 1, ReleaseBuilder.of(TEST, ACTIVE));
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        delete(ruleSet, "1").assertProblem(500, INTERNAL_ERROR.getCode());

        store.assertUnchanged(before);
    }

    /** 管理根当前指针不是最新版本时失败关闭。 */
    @Test
    void test_businessRuleViolation_staleHeadFailsClosed() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, RuleSetVersionStatus.DRAFT));
        ruleSets.corruptCurrentVersion(ruleSet, 1);
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        delete(ruleSet, "1").assertProblem(500, INTERNAL_ERROR.getCode());

        store.assertUnchanged(before);
    }

    static Stream<String> invalidRuleSetIds() {
        return Stream.of("0", "-1", "abc", "012", "1".repeat(20), "9223372036854775808");
    }

    static Stream<String> invalidVersions() {
        return Stream.of("0", "01", "-1", " 1", "1.0", "abc", "", "9223372036854775808", "1".repeat(20));
    }

    static Stream<Arguments> malformedBodies() {
        return Stream.of(Arguments.of("数字版本", "{\"expectedVersion\":1}"),
            Arguments.of("null 版本", "{\"expectedVersion\":null}"), Arguments.of("缺少版本", "{}"),
            Arguments.of("多余字段", "{\"expectedVersion\":\"1\",\"reason\":\"x\"}"),
            Arguments.of("重复键", "{\"expectedVersion\":\"1\",\"expectedVersion\":\"1\"}"),
            Arguments.of("顶层为数组", "[\"1\"]"), Arguments.of("顶层为字符串", "\"1\""),
            Arguments.of("非法 JSON", "{\"expectedVersion\":"), Arguments.of("尾随内容", "{\"expectedVersion\":\"1\"} {}"),
            Arguments.of("空报文", ""));
    }

    /** 根写入删除证据且仅版本、删除字段与更新时间变化；全部版本与规则闭包不变。 */
    private void assertDeleted(Snapshot before, MockedRuleSet ruleSet, Instant time) {
        Snapshot after = store.snapshot(ruleSet.ruleSetCode());
        store.assertRootChanged(before, after, Map.of("status", RuleSetManagementStatus.DELETED.getCode(), "version",
            2L, "deleted_by", ACTOR, "deleted_time", time, "delete_reason_code", "OPERATOR_REQUEST",
            "delete_trigger_id", RuleSetBuilder.sha256("RISK_RULE_SET_DELETE:" + ruleSet.ruleSetId()), "update_time",
            time));
        for (int versionNo = 1; versionNo <= ruleSet.versions().size(); versionNo++) {
            store.assertVersionChanged(before, after, versionNo, Map.of());
        }
    }

    private static String body(String expectedVersion) {
        return "{\"expectedVersion\":\"" + expectedVersion + "\"}";
    }

    private static String id(MockedRuleSet ruleSet) {
        return Long.toString(ruleSet.ruleSetId());
    }

    private RuleSetCommandResponseChecker delete(MockedRuleSet ruleSet, String expectedVersion) {
        return invoke(id(ruleSet), body(expectedVersion));
    }

    private RuleSetCommandResponseChecker invoke(String ruleSetId, String requestBody) {
        return invoke(ruleSetId, requestBody, MediaType.APPLICATION_JSON);
    }

    private RuleSetCommandResponseChecker invoke(String ruleSetId, String requestBody, MediaType contentType) {
        String requestId = "risk-rule-set-delete-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACTOR));
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        headers.setContentType(contentType);
        Instant before = Instant.now();
        var result = http.exchange(HttpMethod.DELETE, PATH.formatted(ruleSetId), headers,
            requestBody.getBytes(StandardCharsets.UTF_8));
        return new RuleSetCommandResponseChecker(result, objectMapper, requestId, before, Instant.now());
    }
}

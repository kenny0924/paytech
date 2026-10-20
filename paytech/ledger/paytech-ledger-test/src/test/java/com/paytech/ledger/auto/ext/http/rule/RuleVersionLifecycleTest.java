package com.paytech.ledger.auto.ext.http.rule;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.ledger.api.enums.AutoInstructionType;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.api.request.RuleLineRequestBody;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleBundleVer;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.test.base.AbstractRuleBundleApiTest;
import com.paytech.ledger.test.builder.RuleVersionRequestBuilder;

import tools.jackson.databind.JsonNode;

/**
 * 记账规则版本生命周期：创建 DRAFT、提交、批准切换当前版本（已过的生效时间记为批准时间）、未到生效时间仅 APPROVED、驳回、Maker 自审、
 * 非 DRAFT 提交与查询。
 *
 * @see com.paytech.ledger.api.AccountingRuleBundleApi#createRuleVersion(String, String, String, com.paytech.ledger.api.request.CreateRuleVersionRequestBody)
 * @see com.paytech.ledger.api.AccountingRuleBundleApi#submitRuleVersion(String, String, String, String)
 * @see com.paytech.ledger.api.AccountingRuleBundleApi#approveRuleVersion(String, String, String, String)
 * @see com.paytech.ledger.api.AccountingRuleBundleApi#rejectRuleVersion(String, String, String, String)
 * @see com.paytech.ledger.api.AccountingRuleBundleApi#getRuleBundle(String)
 * @see com.paytech.ledger.api.AccountingRuleBundleApi#getRuleVersion(String, String)
 */
class RuleVersionLifecycleTest extends AbstractRuleBundleApiTest {

    /** 创建 DRAFT：版本号 = 最大值 + 1，行集按指令类型、行号回显，Bundle 当前版本不变。 */
    @Test
    void test_createDraftEchoesLines() {
        Instant effectiveFrom = pastEffectiveFrom();
        var response = createVersion(RuleVersionRequestBuilder.template(effectiveFrom)).assertSuccess(201)
            .assertText("bundleCode", BUNDLE_CODE).assertText("bundleVer", "2")
            .assertText("status", RuleVersionStatus.DRAFT.getCode()).assertText("makerId", MAKER_ID);

        assertAll(() -> assertEquals(effectiveFrom, Instant.parse(response.text("effectiveFrom"))),
            () -> assertNull(response.text("effectiveTo")), () -> assertNull(response.text("checkerId")),
            () -> assertNull(response.text("approvedTime")));
        assertLinesEqual(RuleVersionRequestBuilder.standardLines(), response.json().path("lines"));
        long versionId = response.id("versionId");
        assertEquals(RuleVersionRequestBuilder.standardLines().size(), testMapper.countRuleLines(versionId));
        assertPointer(INITIAL_VERSION_ID, 0L);
    }

    /** 提交冻结为 PENDING_APPROVAL；Checker 批准后 ACTIVE，指针切换，旧版本 RETIRED 且失效时间 = 新版本生效时间（即批准时间）。 */
    @Test
    void test_approveActivatesAndRetiresPrevious() {
        Instant effectiveFrom = pastEffectiveFrom();
        var submitted = createSubmitted(RuleVersionRequestBuilder.template(effectiveFrom))
            .assertText("status", RuleVersionStatus.PENDING_APPROVAL.getCode());
        long versionId = submitted.id("versionId");

        var approved = approve(2, CHECKER_ID).assertSuccess(200)
            .assertText("status", RuleVersionStatus.ACTIVE.getCode()).assertText("checkerId", CHECKER_ID);
        Instant approvedTime = Instant.parse(approved.text("approvedTime"));
        assertNull(approved.text("effectiveTo"));

        JsonNode bundle = getBundle(BUNDLE_CODE).assertSuccess(200)
            .assertText("status", BUNDLE_ACTIVE).assertText("currentBundleVer", "2").json();
        JsonNode versions = bundle.path("versions");
        assertEquals(2, versions.size());
        assertVersionSummary(versions.get(0), versionId, 2, RuleVersionStatus.ACTIVE, null);
        assertVersionSummary(versions.get(1), INITIAL_VERSION_ID, 1, RuleVersionStatus.RETIRED, approvedTime);
        getVersion(INITIAL_BUNDLE_VER).assertSuccess(200).assertText("status", RuleVersionStatus.RETIRED.getCode());
        assertEquals(RuleVersionStatus.RETIRED.getCode(), testMapper.selectRuleVersionStatus(INITIAL_VERSION_ID));
        assertPointer(versionId, 1L);
    }

    /**
     * AC-07：审批时生效时间已过（一小时前）：生效时间记为批准时间，旧版本失效时间为同一时间，审批前的时段仍归旧版本
     * <li>响应、版本行与旧版本行三处一致；批准时间晚于编制时填写的生效时间。</li>
     */
    @Test
    void test_pastEffectiveFromRewrittenToApprovalTime() {
        Instant requested = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.HOURS);
        long versionId = createSubmitted(RuleVersionRequestBuilder.template(requested)).id("versionId");

        var approved = approve(2, CHECKER_ID).assertSuccess(200)
            .assertText("status", RuleVersionStatus.ACTIVE.getCode());

        Instant approvedTime = Instant.parse(approved.text("approvedTime"));
        AccountingRuleBundleVer activated = testMapper.selectRuleVersion(versionId);
        AccountingRuleBundleVer retired = testMapper.selectRuleVersion(INITIAL_VERSION_ID);
        assertAll(() -> assertTrue(approvedTime.isAfter(requested), "批准时间晚于填写的生效时间"),
            () -> assertEquals(approvedTime, Instant.parse(approved.text("effectiveFrom")), "响应生效时间"),
            () -> assertEquals(approvedTime, activated.getEffectiveFrom(), "版本生效时间"),
            () -> assertEquals(approvedTime, activated.getApprovedTime()),
            () -> assertNull(activated.getEffectiveTo()),
            () -> assertEquals(RuleVersionStatus.RETIRED, retired.getStatus()),
            () -> assertEquals(approvedTime, retired.getEffectiveTo(), "旧版本失效时间"));
        assertPointer(versionId, 1L);
    }

    /** 生效时间在未来：批准后只到 APPROVED，生效时间不变，当前版本指针与旧版本保持不变。 */
    @Test
    void test_futureEffectiveApprovalStaysApproved() {
        Instant future = Instant.now().truncatedTo(ChronoUnit.MICROS).plus(1, ChronoUnit.DAYS);
        long versionId = createSubmitted(RuleVersionRequestBuilder.template(future)).id("versionId");

        var approved = approve(2, CHECKER_ID).assertSuccess(200)
            .assertText("status", RuleVersionStatus.APPROVED.getCode()).assertText("checkerId", CHECKER_ID);
        assertNotNull(approved.text("approvedTime"));
        assertAll(() -> assertEquals(future, Instant.parse(approved.text("effectiveFrom")), "响应生效时间不变"),
            () -> assertEquals(future, testMapper.selectRuleVersion(versionId).getEffectiveFrom(), "版本生效时间不变"));

        getBundle(BUNDLE_CODE).assertSuccess(200).assertText("currentBundleVer", "1");
        assertEquals(RuleVersionStatus.ACTIVE.getCode(), testMapper.selectRuleVersionStatus(INITIAL_VERSION_ID));
        assertPointer(INITIAL_VERSION_ID, 0L);
    }

    /** 驳回为 REJECTED、不写批准时间；驳回后不再阻塞新建版本。 */
    @Test
    void test_rejectMarksRejected() {
        createSubmitted(version());

        var rejected = reject(2, CHECKER_ID).assertSuccess(200)
            .assertText("status", RuleVersionStatus.REJECTED.getCode()).assertText("checkerId", CHECKER_ID);
        assertNull(rejected.text("approvedTime"));
        assertPointer(INITIAL_VERSION_ID, 0L);

        createVersion(version()).assertSuccess(201).assertText("bundleVer", "3");
    }

    /** 已到生效时间但生效时间不晚于当前版本（2000-01-01）：批准被拒，版本保持 PENDING_APPROVAL，指针不变。 */
    @Test
    void test_effectiveFromNotAfterCurrentRejected() {
        createSubmitted(RuleVersionRequestBuilder.template(Instant.parse("1999-01-01T00:00:00Z")));

        approve(2, CHECKER_ID).assertRejected(LedgerErrorCode.RULE_EFFECTIVE_FROM_INVALID);

        getVersion(2).assertSuccess(200).assertText("status", RuleVersionStatus.PENDING_APPROVAL.getCode());
        assertPointer(INITIAL_VERSION_ID, 0L);
    }

    /** Maker 不能批准或驳回自己的版本，版本保持 PENDING_APPROVAL。 */
    @Test
    void test_makerSelfReviewRejected() {
        createSubmitted(version());

        approve(2, MAKER_ID).assertRejected(LedgerErrorCode.SELF_REVIEW_FORBIDDEN);
        reject(2, MAKER_ID).assertRejected(LedgerErrorCode.SELF_REVIEW_FORBIDDEN);

        getVersion(2).assertSuccess(200).assertText("status", RuleVersionStatus.PENDING_APPROVAL.getCode());
        assertPointer(INITIAL_VERSION_ID, 0L);
    }

    /** 只有 DRAFT 且由编制人提交；已提交、已生效版本再提交或复核非待审版本均被拒。 */
    @Test
    void test_nonDraftSubmitRejected() {
        createVersion(version()).assertSuccess(201);

        submit(2, CHECKER_ID).assertRejected(LedgerErrorCode.RULE_SUBMITTER_NOT_MAKER);
        submit(2, MAKER_ID).assertSuccess(200);
        submit(2, MAKER_ID).assertRejected(LedgerErrorCode.RULE_VERSION_STATUS_INVALID);
        submit(INITIAL_BUNDLE_VER, MAKER_ID).assertRejected(LedgerErrorCode.RULE_VERSION_STATUS_INVALID);
        approve(INITIAL_BUNDLE_VER, CHECKER_ID).assertRejected(LedgerErrorCode.RULE_VERSION_STATUS_INVALID);

        getVersion(2).assertSuccess(200).assertText("status", RuleVersionStatus.PENDING_APPROVAL.getCode());
    }

    /** 已有 DRAFT 或 PENDING_APPROVAL 版本时再创建被拒，不新增版本。 */
    @Test
    void test_pendingVersionBlocksCreate() {
        createVersion(version()).assertSuccess(201);
        createVersion(version()).assertRejected(LedgerErrorCode.RULE_VERSION_IN_PROGRESS);

        submit(2, MAKER_ID).assertSuccess(200);
        createVersion(version()).assertRejected(LedgerErrorCode.RULE_VERSION_IN_PROGRESS);

        assertEquals(2, getBundle(BUNDLE_CODE).assertSuccess(200).json().path("versions").size());
    }

    /** 某指令类型只有借方行：创建成功，提交被拒并保持 DRAFT。 */
    @Test
    void test_submitUnbalancedInstructionTypeRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.removeLine(RuleVersionRequestBuilder.standardLines().size() - 1);
        createVersion(builder).assertSuccess(201).assertText("status", RuleVersionStatus.DRAFT.getCode());

        submit(2, MAKER_ID).assertRejected(LedgerErrorCode.RULE_LINE_INVALID);

        getVersion(2).assertSuccess(200).assertText("status", RuleVersionStatus.DRAFT.getCode());
    }

    /** Bundle 不存在：创建与查询均为业务错误。 */
    @Test
    void test_bundleNotFound() {
        String unknown = "UNKNOWN_BUNDLE";

        createVersion(unknown, version()).assertBusinessRejected(unknown);
        getBundle(unknown).assertBusinessRejected(unknown);
    }

    /** 查询：版本详情含 10 条初始化规则行（Owner 行 5202、5208 已删除）；Bundle 版本摘要按版本号降序。 */
    @Test
    void test_queryVersionAndBundleDescending() {
        createSubmitted(version());
        reject(2, CHECKER_ID).assertSuccess(200);
        long thirdId = createVersion(version()).assertSuccess(201).id("versionId");

        var initial = getVersion(INITIAL_BUNDLE_VER).assertSuccess(200)
            .assertText("versionId", String.valueOf(INITIAL_VERSION_ID)).assertText("bundleVer", "1")
            .assertText("status", RuleVersionStatus.ACTIVE.getCode());
        JsonNode lines = initial.json().path("lines");
        assertLinesEqual(RuleVersionRequestBuilder.standardLines(), lines);
        assertEquals(List.of("5201", "5203", "5204", "5205", "5206", "5207", "5209", "5210", "5211", "5212"),
            lines.valueStream().map(line -> line.path("ruleLineId").asString()).toList(), "规则行主键顺序");

        JsonNode versions = getBundle(BUNDLE_CODE).assertSuccess(200).assertText("bundleCode", BUNDLE_CODE)
            .assertText("bundleId", String.valueOf(BUNDLE_ID)).assertText("currentBundleVer", "1").json()
            .path("versions");
        assertEquals(3, versions.size());
        assertVersionSummary(versions.get(0), thirdId, 3, RuleVersionStatus.DRAFT, null);
        assertEquals(RuleVersionStatus.REJECTED.getCode(), versions.get(1).path("status").asString());
        assertVersionSummary(versions.get(2), INITIAL_VERSION_ID, 1, RuleVersionStatus.ACTIVE, null);
    }

    /** 行集与期望逐行逐字段相同（顺序一致）。 */
    private void assertLinesEqual(List<RuleLineRequestBody> expected, JsonNode actual) {
        assertEquals(expected.size(), actual.size(), "规则行数");
        for (int i = 0; i < expected.size(); i++) {
            RuleLineRequestBody line = expected.get(i);
            JsonNode node = actual.get(i);
            assertTrue(!node.path("ruleLineId").asString().isEmpty(), "ruleLineId");
            assertAll("规则行 " + i,
                () -> assertEquals(line.getInstructionType(), node.path("instructionType").asString()),
                () -> assertEquals(line.getLineNo().intValue(), node.path("lineNo").asInt()),
                () -> assertEquals(line.getLineCode(), node.path("lineCode").asString()),
                () -> assertEquals(line.getAmountSourceCode(), node.path("amountSourceCode").asString()),
                () -> assertEquals(line.getOwnerRole(), node.path("ownerRole").asString()),
                () -> assertEquals(line.getAccountCode(), node.path("accountCode").asString()),
                () -> assertEquals(line.getBalanceBucket(), node.path("balanceBucket").asString()),
                () -> assertEquals(line.getEntrySide(), node.path("entrySide").asString()),
                () -> assertEquals(line.getRequiredFlag(), node.path("requiredFlag").asBoolean()));
        }
    }

    /** 版本摘要断言；expectedEffectiveTo 为 null 表示未失效。 */
    private void assertVersionSummary(JsonNode node, long versionId, long bundleVer, RuleVersionStatus status,
        Instant expectedEffectiveTo) {
        JsonNode effectiveTo = node.path("effectiveTo");
        assertAll("版本 " + bundleVer,
            () -> assertEquals(String.valueOf(versionId), node.path("versionId").asString()),
            () -> assertEquals(bundleVer, node.path("bundleVer").asLong()),
            () -> assertEquals(status.getCode(), node.path("status").asString()),
            () -> assertEquals(expectedEffectiveTo,
                effectiveTo.isMissingNode() || effectiveTo.isNull() ? null : Instant.parse(effectiveTo.asString())));
    }
}

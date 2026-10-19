package com.paytech.ledger.auto.ext.http.rule;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.ledger.api.enums.AutoInstructionType;
import com.paytech.ledger.api.request.RuleLineRequestBody;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.test.base.AbstractRuleBundleApiTest;
import com.paytech.ledger.test.builder.RuleVersionRequestBuilder;

import tools.jackson.databind.JsonNode;

/**
 * 记账规则版本生命周期：创建 DRAFT、提交、批准切换当前版本、未到生效时间仅 APPROVED、驳回、Maker 自审、非 DRAFT 提交与查询。
 *
 * @see com.paytech.ledger.api.AccountingRuleBundleApi#createRuleVersion(String, com.paytech.ledger.api.request.CreateRuleVersionRequestBody)
 * @see com.paytech.ledger.api.AccountingRuleBundleApi#submitRuleVersion(String, String, com.paytech.ledger.api.request.SubmitRuleVersionRequestBody)
 * @see com.paytech.ledger.api.AccountingRuleBundleApi#approveRuleVersion(String, String, com.paytech.ledger.api.request.ReviewRuleVersionRequestBody)
 * @see com.paytech.ledger.api.AccountingRuleBundleApi#rejectRuleVersion(String, String, com.paytech.ledger.api.request.ReviewRuleVersionRequestBody)
 * @see com.paytech.ledger.api.AccountingRuleBundleApi#getRuleBundle(String)
 * @see com.paytech.ledger.api.AccountingRuleBundleApi#getRuleVersion(String, String)
 */
class RuleVersionLifecycleTest extends AbstractRuleBundleApiTest {

    /** 创建 DRAFT：版本号 = 最大值 + 1，行集按指令类型、行号回显，Bundle 当前版本不变。 */
    @Test
    void test_createDraftEchoesLines() {
        Instant effectiveFrom = pastEffectiveFrom();
        var response = createVersion(RuleVersionRequestBuilder.template(MAKER_ID, effectiveFrom)).assertSuccess(201)
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

    /** 提交冻结为 PENDING_APPROVAL；Checker 批准后 ACTIVE，指针切换，旧版本 RETIRED 且失效时间 = 新版本生效时间。 */
    @Test
    void test_approveActivatesAndRetiresPrevious() {
        Instant effectiveFrom = pastEffectiveFrom();
        var submitted = createSubmitted(RuleVersionRequestBuilder.template(MAKER_ID, effectiveFrom))
            .assertText("status", RuleVersionStatus.PENDING_APPROVAL.getCode());
        long versionId = submitted.id("versionId");

        var approved = approve(2, CHECKER_ID).assertSuccess(200)
            .assertText("status", RuleVersionStatus.ACTIVE.getCode()).assertText("checkerId", CHECKER_ID);
        assertNotNull(approved.text("approvedTime"));
        assertNull(approved.text("effectiveTo"));

        JsonNode bundle = getBundle(BUNDLE_CODE).assertSuccess(200)
            .assertText("status", BUNDLE_ACTIVE).assertText("currentBundleVer", "2").json();
        JsonNode versions = bundle.path("versions");
        assertEquals(2, versions.size());
        assertVersionSummary(versions.get(0), versionId, 2, RuleVersionStatus.ACTIVE, null);
        assertVersionSummary(versions.get(1), INITIAL_VERSION_ID, 1, RuleVersionStatus.RETIRED, effectiveFrom);
        getVersion(INITIAL_BUNDLE_VER).assertSuccess(200).assertText("status", RuleVersionStatus.RETIRED.getCode());
        assertEquals(RuleVersionStatus.RETIRED.getCode(), testMapper.selectRuleVersionStatus(INITIAL_VERSION_ID));
        assertPointer(versionId, 1L);
    }

    /** 生效时间在未来：批准后只到 APPROVED，当前版本指针与旧版本保持不变。 */
    @Test
    void test_futureEffectiveApprovalStaysApproved() {
        Instant future = Instant.now().truncatedTo(ChronoUnit.MICROS).plus(1, ChronoUnit.DAYS);
        createSubmitted(RuleVersionRequestBuilder.template(MAKER_ID, future));

        var approved = approve(2, CHECKER_ID).assertSuccess(200)
            .assertText("status", RuleVersionStatus.APPROVED.getCode()).assertText("checkerId", CHECKER_ID);
        assertNotNull(approved.text("approvedTime"));

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

    /** Maker 不能批准或驳回自己的版本，版本保持 PENDING_APPROVAL。 */
    @Test
    void test_makerSelfReviewRejected() {
        createSubmitted(version());

        approve(2, MAKER_ID).assertBusinessRejected(MAKER_ID);
        reject(2, MAKER_ID).assertBusinessRejected(MAKER_ID);

        getVersion(2).assertSuccess(200).assertText("status", RuleVersionStatus.PENDING_APPROVAL.getCode());
        assertPointer(INITIAL_VERSION_ID, 0L);
    }

    /** 只有 DRAFT 且由编制人提交；已提交、已生效版本再提交或复核非待审版本均被拒。 */
    @Test
    void test_nonDraftSubmitRejected() {
        createVersion(version()).assertSuccess(201);

        submit(2, CHECKER_ID).assertBusinessRejected(CHECKER_ID);
        submit(2, MAKER_ID).assertSuccess(200);
        submit(2, MAKER_ID).assertBusinessRejected(RuleVersionStatus.PENDING_APPROVAL.getCode());
        submit(INITIAL_BUNDLE_VER, MAKER_ID).assertBusinessRejected(RuleVersionStatus.ACTIVE.getCode());
        approve(INITIAL_BUNDLE_VER, CHECKER_ID).assertBusinessRejected(RuleVersionStatus.ACTIVE.getCode());

        getVersion(2).assertSuccess(200).assertText("status", RuleVersionStatus.PENDING_APPROVAL.getCode());
    }

    /** 已有 DRAFT 或 PENDING_APPROVAL 版本时再创建被拒，不新增版本。 */
    @Test
    void test_pendingVersionBlocksCreate() {
        createVersion(version()).assertSuccess(201);
        createVersion(version()).assertBusinessRejected(BUNDLE_CODE);

        submit(2, MAKER_ID).assertSuccess(200);
        createVersion(version()).assertBusinessRejected(BUNDLE_CODE);

        assertEquals(2, getBundle(BUNDLE_CODE).assertSuccess(200).json().path("versions").size());
    }

    /** 某指令类型只有借方行：创建成功，提交被拒并保持 DRAFT。 */
    @Test
    void test_submitUnbalancedInstructionTypeRejected() {
        RuleVersionRequestBuilder builder = version();
        builder.removeLine(RuleVersionRequestBuilder.standardLines().size() - 1);
        createVersion(builder).assertSuccess(201).assertText("status", RuleVersionStatus.DRAFT.getCode());

        submit(2, MAKER_ID).assertBusinessRejected(AutoInstructionType.PLATFORM_SERVICE_CHARGE_PLATFORM.getCode());

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

    /** Bundle 当前版本指针与乐观锁版本。 */
    private void assertPointer(long currentVerId, long version) {
        Map<String, Object> pointer = testMapper.selectRuleBundlePointer(BUNDLE_ID);
        assertEquals(currentVerId, ((Number)pointer.get("current_ver_id")).longValue(), "current_ver_id");
        assertEquals(version, ((Number)pointer.get("version")).longValue(), "Bundle version");
    }
}

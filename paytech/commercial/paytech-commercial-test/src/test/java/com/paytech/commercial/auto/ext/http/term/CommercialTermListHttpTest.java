package com.paytech.commercial.auto.ext.http.term;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.api.term.CommercialTermQueryApi;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.BusinessChangeRecordRecordType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.test.base.term.AbstractCommercialTermApiTest;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.CommercialTermPlanBuilder;
import com.paytech.commercial.test.builder.CommercialTermVersionBuilder;
import com.paytech.foundation.common.error.CommonErrorCode;

import tools.jackson.databind.JsonNode;

/**
 * 商业条款方案 / 版本列表与审批工作台（运营控制台第二切片 technique.md §2）：筛选、生效窗口、工作台排序、经正式写入 API 产生的
 * Maker / Checker / 提交时刻与价格条数、方案摘要的 ACTIVE / SCHEDULED 指针、方案聚合的变更历史时间线、参数校验
 *
 * @see CommercialTermQueryApi
 */
class CommercialTermListHttpTest extends AbstractCommercialTermApiTest {
    private static final String PLANS = TERMS + "/plans";

    private static final String VERSIONS = TERMS + "/versions";

    /** 预置方案 ID 基数（与版本 ID、BCR 偏移不冲突）。 */
    private static final long PLAN = 930000000007000L;

    /** 预置版本 ID 基数。 */
    private static final long VERSION = 930000000008000L;

    /** 待审批按提交时刻升序、待生效按生效起点升序；两种状态一起查询时按方案、版本号倒序。 */
    @Test
    void test_worklistOrdering() {
        mockPlan(1, version(11, 1, CommercialTermVersionStatus.PENDING_APPROVAL).updateTime(hoursAgo(1)),
            version(12, 2, CommercialTermVersionStatus.SCHEDULED).effectiveFrom(Instant.parse("2026-10-05T00:00:00Z")));
        mockPlan(2, version(21, 1, CommercialTermVersionStatus.PENDING_APPROVAL).updateTime(hoursAgo(3)),
            version(22, 2, CommercialTermVersionStatus.SCHEDULED).effectiveFrom(Instant.parse("2026-10-01T00:00:00Z")));
        mockPlan(3, version(31, 1, CommercialTermVersionStatus.PENDING_APPROVAL).updateTime(hoursAgo(2)),
            version(32, 2, CommercialTermVersionStatus.DRAFT));

        JsonNode pending = list(VERSIONS + "?status=PENDING_APPROVAL");
        JsonNode scheduled = list(VERSIONS + "?status=SCHEDULED");
        JsonNode both = list(VERSIONS + "?status=PENDING_APPROVAL&status=SCHEDULED");

        assertAll(() -> assertEquals(List.of(VERSION + 21, VERSION + 31, VERSION + 11), ids(pending, "versionId")),
            () -> assertEquals(3, pending.path("total").asLong()),
            () -> assertEquals(List.of(VERSION + 22, VERSION + 12), ids(scheduled, "versionId")),
            () -> assertEquals("2026-10-01T00:00:00Z", scheduled.path("items").get(0).path("effectiveFrom").asString()),
            () -> assertEquals(List.of(VERSION + 31, VERSION + 22, VERSION + 21, VERSION + 12, VERSION + 11),
                ids(both, "versionId")),
            // 预置版本没有审计快照：审批事实为空，价格条数 0
            () -> assertTrue(pending.path("items").get(0).path("makerAccountId").isNull()),
            () -> assertEquals(0, pending.path("items").get(0).path("priceCount").asInt()),
            () -> assertEquals(Long.toString(PLAN + 2), pending.path("items").get(0).path("planId").asString()),
            () -> assertEquals("3", pending.path("items").get(0).path("changeVersion").asString()));
    }

    /** 方案 / 合同筛选与生效窗口相交（含无终点版本）；窗口终点不晚于起点 400。 */
    @Test
    void test_versionFiltersAndWindow() {
        mockPlan(1, version(11, 1, CommercialTermVersionStatus.SUPERSEDED).effectiveTo(Instant.parse("2026-09-15T00:00:00Z")),
            version(12, 2, CommercialTermVersionStatus.ACTIVE).activeSlot(1)
                .effectiveFrom(Instant.parse("2026-09-15T00:00:00Z")),
            version(13, 3, CommercialTermVersionStatus.DRAFT).effectiveFrom(Instant.parse("2026-10-01T00:00:00Z")));
        mockPlan(2, version(21, 1, CommercialTermVersionStatus.DRAFT));
        String plan = VERSIONS + "?planId=" + (PLAN + 1);

        assertAll(() -> assertEquals(List.of(VERSION + 13, VERSION + 12, VERSION + 11), ids(list(plan), "versionId")),
            () -> assertEquals(List.of(VERSION + 21), ids(list(VERSIONS + "?agreementId=" + agreementId(2)), "versionId")),
            () -> assertEquals(List.of(VERSION + 13, VERSION + 12),
                ids(list(plan + "&effectiveFrom=2026-09-20T00:00:00Z"), "versionId")),
            () -> assertEquals(List.of(VERSION + 11),
                ids(list(plan + "&effectiveFrom=2026-09-01T00:00:00Z&effectiveTo=2026-09-10T00:00:00Z"), "versionId")),
            () -> assertEquals(List.of(VERSION + 12, VERSION + 11),
                ids(list(plan + "&effectiveFrom=2026-09-10T00:00:00Z&effectiveTo=2026-09-16T00:00:00Z"),
                    "versionId")));
        assertProblem(http.get(plan + "&effectiveFrom=2026-09-10T00:00:00Z&effectiveTo=2026-09-10T00:00:00Z", headers()),
            400, CommonErrorCode.INVALID_REQUEST.getCode(), "effectiveTo");
    }

    /** 方案列表：筛选、创建时间倒序、根版本与 ACTIVE / SCHEDULED 指针。 */
    @Test
    void test_planList() {
        mockPlan(1, version(11, 1, CommercialTermVersionStatus.ACTIVE).activeSlot(1),
            version(12, 2, CommercialTermVersionStatus.SCHEDULED).effectiveFrom(Instant.parse("2026-10-01T00:00:00Z")));
        pricing.mockPlan(plan(2).termType(CommercialCodes.TermType.PAYOUT_FEE).createTime(hoursAgo(1)), 1);
        pricing.mockPlan(plan(3).clientId(930000000000999L).status(CommercialTermPlanStatus.DRAFT).createTime(hoursAgo(2)), 1);

        JsonNode all = list(PLANS);
        JsonNode first = all.path("items").get(0);
        assertAll(() -> assertEquals(List.of(PLAN + 1, PLAN + 2, PLAN + 3), ids(all, "planId")),
            () -> assertEquals(3, all.path("total").asLong()),
            () -> assertEquals("3", first.path("changeVersion").asString()),
            () -> assertEquals(Long.toString(VERSION + 11), first.path("activeVersionId").asString()),
            () -> assertEquals("1", first.path("activeVersionNo").asString()),
            () -> assertEquals("2026-09-01T00:00:00Z", first.path("activeEffectiveFrom").asString()),
            () -> assertEquals(Long.toString(VERSION + 12), first.path("scheduledVersionId").asString()),
            () -> assertEquals("2026-10-01T00:00:00Z", first.path("scheduledEffectiveFrom").asString()),
            () -> assertTrue(all.path("items").get(1).path("activeVersionId").isNull()),
            () -> assertEquals(List.of(PLAN + 2), ids(list(PLANS + "?termType=PAYOUT_FEE"), "planId")),
            () -> assertEquals(List.of(PLAN + 3), ids(list(PLANS + "?clientId=930000000000999"), "planId")),
            () -> assertEquals(List.of(PLAN + 1, PLAN + 2), ids(list(PLANS + "?status=ACTIVE"), "planId")),
            () -> assertEquals(List.of(PLAN + 2), ids(list(PLANS + "?agreementId=" + agreementId(2)), "planId")));
    }

    /** 经正式写入 API：提交记 Maker 与提交时刻；驳回清空；批准记 Checker；排期与激活后仍保留本轮审批事实；方案摘要指向 ACTIVE 版本。 */
    @Test
    void test_reviewFactsFromWriteApi() {
        mockBaseline();
        JsonNode plan = createPlan();
        JsonNode priced = replacePrices(createVersion(plan), price("USD", CARD, 1, percentage("MDR", "0.025")),
            price("USD", CARD_VISA, 2, percentage("MDR", "0.02")));
        JsonNode submitted = command(priced, "submit", MAKER);

        JsonNode pending = only(list(VERSIONS + "?status=PENDING_APPROVAL"));
        assertAll(() -> assertEquals(Long.toString(MAKER), pending.path("makerAccountId").asString()),
            () -> assertTrue(pending.path("checkerAccountId").isNull()),
            () -> assertEquals(NOW.toString(), pending.path("submittedTime").asString()),
            () -> assertEquals(2, pending.path("priceCount").asInt()),
            () -> assertEquals(text(submitted, "changeVersion"), pending.path("changeVersion").asString()),
            () -> assertEquals("PLAN-C-1", pending.path("planCode").asString()),
            () -> assertEquals(CommercialCodes.TermType.CLIENT_PRICING, pending.path("termType").asString()),
            () -> assertEquals(Long.toString(AgreementBuilder.CLIENT_ID), pending.path("clientId").asString()));

        JsonNode rejected = command(submitted, "reject", CHECKER);
        String byPlan = VERSIONS + "?planId=" + text(plan, "planId");
        assertTrue(only(list(byPlan)).path("makerAccountId").isNull());

        JsonNode approvedVersion = command(command(rejected, "submit", MAKER), "approve", CHECKER);
        JsonNode approved = only(list(VERSIONS + "?status=APPROVED"));
        assertAll(() -> assertEquals(Long.toString(MAKER), approved.path("makerAccountId").asString()),
            () -> assertEquals(Long.toString(CHECKER), approved.path("checkerAccountId").asString()));

        JsonNode active = command(ok(200, scheduleResult(approvedVersion, NOW)), "activate", CHECKER);
        JsonNode activeRow = only(list(byPlan));
        JsonNode planRow = only(list(PLANS + "?agreementId=" + AgreementBuilder.AGREEMENT_ID));
        assertAll(() -> assertEquals(CommercialTermVersionStatus.ACTIVE.getCode(), activeRow.path("status").asString()),
            () -> assertEquals(Long.toString(MAKER), activeRow.path("makerAccountId").asString()),
            () -> assertEquals(Long.toString(CHECKER), activeRow.path("checkerAccountId").asString()),
            () -> assertEquals(text(active, "versionId"), planRow.path("activeVersionId").asString()),
            () -> assertEquals(text(active, "changeVersion"), planRow.path("changeVersion").asString()),
            () -> assertTrue(planRow.path("scheduledVersionId").isNull()));
    }

    /** 方案聚合的变更历史：版本倒序，同一命令 Root 快照在 Child 之前且共享 triggerId；首条为方案创建。 */
    @Test
    void test_businessChangeTimeline_termPlan() {
        mockBaseline();
        JsonNode plan = createPlan();
        command(replacePrices(createVersion(plan), price("USD", CARD, 1, percentage("MDR", "0.025"))), "submit", MAKER);

        JsonNode timeline = list("/business-changes/v1?aggregateRootType=COMMERCIAL_TERM_PLAN&aggregateRootId="
            + text(plan, "planId"));
        JsonNode items = timeline.path("items");
        JsonNode submitRoot = items.get(0);
        JsonNode submitChild = items.get(1);
        JsonNode created = items.get(items.size() - 1);
        assertAll(() -> assertEquals(7, timeline.path("total").asLong()),
            () -> assertEquals(4, submitRoot.path("versionNo").asLong()),
            () -> assertEquals(BusinessChangeRecordRecordType.COMMERCIAL_TERM_PLAN.getCode(),
                submitRoot.path("recordType").asString()),
            () -> assertEquals(BusinessChangeRecordRecordType.COMMERCIAL_TERM_VERSION.getCode(),
                submitChild.path("recordType").asString()),
            () -> assertEquals(4, submitChild.path("versionNo").asLong()),
            () -> assertEquals(submitRoot.path("meta").path("triggerId").asString(),
                submitChild.path("meta").path("triggerId").asString()),
            () -> assertEquals(BusinessChangeReason.TERM_VERSION_SUBMITTED.getCode(),
                submitChild.path("meta").path("reasonCode").asString()),
            () -> assertEquals(CommercialTermVersionStatus.PENDING_APPROVAL.getCode(),
                submitChild.path("data").path("status").asString()),
            () -> assertEquals(Long.toString(MAKER), submitChild.path("createBy").asString()),
            () -> assertEquals(BusinessChangeReason.TERM_PLAN_CREATED.getCode(),
                created.path("meta").path("reasonCode").asString()),
            () -> assertEquals("CREATE", created.path("operationType").asString()),
            () -> assertTrue(submitRoot.path("meta").path("integrityHash").isMissingNode()));
    }

    /** 未知状态、非规范 ID、无时区时间、非法条款类型与分页均 400 INVALID_REQUEST。 */
    @Test
    void test_invalidParameter() {
        for (String path : List.of(VERSIONS + "?status=OPEN", VERSIONS + "?planId=abc",
            VERSIONS + "?effectiveFrom=2026-09-01", VERSIONS + "?size=7", PLANS + "?status=SUSPENDED",
            PLANS + "?termType=client_pricing", PLANS + "?agreementId=0")) {
            assertProblem(http.get(path, headers()), 400, CommonErrorCode.INVALID_REQUEST.getCode(), "");
        }
    }

    // ==================== 前置数据 ====================

    /** 预置方案 index（合同 ID 各不相同，BCR 根修订 3）及其版本。 */
    private void mockPlan(int index, CommercialTermVersionBuilder... versions) {
        for (CommercialTermVersionBuilder version : versions) {
            version.agreementId(agreementId(index));
        }
        pricing.mockPlan(plan(index).status(CommercialTermPlanStatus.ACTIVE), 3, versions);
    }

    /** 方案 index 的 Builder。 */
    private static CommercialTermPlanBuilder plan(int index) {
        return CommercialTermPlanBuilder.template().termPlanId(PLAN + index).planCode("LIST-PLAN-" + index)
            .agreementId(agreementId(index)).status(CommercialTermPlanStatus.ACTIVE);
    }

    /** 版本 Builder：ID 为基数 + id。 */
    private static CommercialTermVersionBuilder version(int id, long versionNo, CommercialTermVersionStatus status) {
        return CommercialTermVersionBuilder.template().termVersionId(VERSION + id).versionNo(versionNo).status(status);
    }

    /** 方案 index 的合同 ID。 */
    private static long agreementId(int index) {
        return AgreementBuilder.AGREEMENT_ID + 50 + index;
    }

    /** 固定时点前若干小时。 */
    private static Instant hoursAgo(int hours) {
        return NOW.minus(Duration.ofHours(hours));
    }

    // ==================== 断言 ====================

    /** GET 并断言 200。 */
    private JsonNode list(String path) {
        return ok(200, http.get(path, headers()));
    }

    /** 唯一的一行。 */
    private static JsonNode only(JsonNode page) {
        assertEquals(1, page.path("items").size(), page.toString());
        return page.path("items").get(0);
    }

    /** 当前页某 ID 字段。 */
    private static List<Long> ids(JsonNode page, String field) {
        return page.path("items").valueStream().map(item -> Long.parseLong(item.path(field).asString())).toList();
    }
}

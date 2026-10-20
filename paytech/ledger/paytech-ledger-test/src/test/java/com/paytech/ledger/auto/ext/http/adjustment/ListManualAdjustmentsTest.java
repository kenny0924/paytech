package com.paytech.ledger.auto.ext.http.adjustment;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.ledger.api.ManualAdjustmentApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.enums.AdjustmentStatus;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.test.base.AbstractManualAdjustmentApiTest;
import com.paytech.ledger.test.builder.SubmitManualAdjustmentRequestBuilder;
import com.paytech.ledger.test.checker.LedgerResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 人工调整列表：按账簿必填、期间 / 状态 / 编制人筛选，按提交时间倒序稳定分页，已过账行带分录主键，金额按币种精度补齐。
 *
 * @see ManualAdjustmentApi#listManualAdjustments(String, String, String, String, Integer, Integer)
 */
class ListManualAdjustmentsTest extends AbstractManualAdjustmentApiTest {
    /** 第二个编制人。 */
    private static final String OTHER_MAKER_ID = "70009";

    private long historyPeriodId;

    private long submittedId;

    private long postedId;

    private long journalEntryId;

    private long rejectedId;

    private long historyId;

    /**
     * 每例按顺序提交 4 笔调整：当月期间待复核、已过账、已驳回各一笔（模板编制人），2020-01 历史期间一笔待复核（第二个编制人）。
     */
    @BeforeEach
    void setUpListMocks() {
        historyPeriodId = ledgerMocker.mockPeriod(ledger.getFiscalCalendarId(), 2020, 1, LocalDate.of(2020, 1, 1),
            LocalDate.of(2020, 1, 31), PeriodStatus.OPEN);
        submittedId = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "1")).id("manualAdjustmentId");
        var posted = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "2.5"));
        postedId = posted.id("manualAdjustmentId");
        journalEntryId = approve(postedId, CHECKER_ID, posted.text("contentHash")).assertSuccess(200).id("journalEntryId");
        var rejected = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "3"));
        rejectedId = rejected.id("manualAdjustmentId");
        reject(rejectedId, CHECKER_ID, rejected.text("contentHash")).assertSuccess(200);
        historyId = submit(SubmitManualAdjustmentRequestBuilder.template(ledger.getLedgerBookId(), historyPeriodId)
            .transfer(bankCashAccountId, capitalAccountId, "4"), OTHER_MAKER_ID).assertSuccess(201).id("manualAdjustmentId");
    }

    /** 只按账簿：4 笔按提交时间倒序，摘要字段与分录主键正确。 */
    @Test
    void test_listByBookNewestFirst() {
        List<JsonNode> items = list("").assertSuccess(200).assertPage(1, 10, 4);

        assertEquals(List.of(historyId, rejectedId, postedId, submittedId), ids(items));
        JsonNode posted = items.get(2);
        assertAll(() -> assertEquals(String.valueOf(ledger.getLedgerBookId()), posted.path("ledgerBookId").asString()),
            () -> assertEquals(String.valueOf(ledger.getOpenPeriodId()), posted.path("accountingPeriodId").asString()),
            () -> assertEquals(ledger.getCurrency(), posted.path("currency").asString()),
            () -> assertEquals(AdjustmentStatus.POSTED.getCode(), posted.path("status").asString()),
            () -> assertEquals("2.50", posted.path("totalDebit").asString()),
            () -> assertEquals("2.50", posted.path("totalCredit").asString()),
            () -> assertEquals(SubmitManualAdjustmentRequestBuilder.MAKER_ID, posted.path("makerId").asString()),
            () -> assertEquals(String.valueOf(journalEntryId), posted.path("journalEntryId").asString()),
            () -> assertEquals(false, posted.path("adjustmentNo").asString().isEmpty()),
            () -> assertEquals(false, posted.path("submittedTime").asString().isEmpty()));
        assertAll(() -> assertNull(text(items.get(0), "journalEntryId")), () -> assertNull(text(items.get(1), "journalEntryId")),
            () -> assertNull(text(items.get(3), "journalEntryId")));
    }

    /** 期间、状态、编制人单独与组合筛选。 */
    @Test
    void test_filters() {
        assertEquals(List.of(rejectedId, postedId, submittedId),
            ids(list("&accountingPeriodId=" + ledger.getOpenPeriodId()).assertSuccess(200).assertPage(1, 10, 3)));
        assertEquals(List.of(historyId),
            ids(list("&accountingPeriodId=" + historyPeriodId).assertSuccess(200).assertPage(1, 10, 1)));
        assertEquals(List.of(historyId, submittedId),
            ids(list("&status=" + AdjustmentStatus.SUBMITTED.getCode()).assertSuccess(200).assertPage(1, 10, 2)));
        assertEquals(List.of(postedId), ids(list("&status=" + AdjustmentStatus.POSTED.getCode()).assertSuccess(200).assertPage(1, 10, 1)));
        assertEquals(List.of(rejectedId),
            ids(list("&status=" + AdjustmentStatus.REJECTED.getCode()).assertSuccess(200).assertPage(1, 10, 1)));
        assertEquals(List.of(historyId), ids(list("&makerId=" + OTHER_MAKER_ID).assertSuccess(200).assertPage(1, 10, 1)));
        assertEquals(List.of(submittedId), ids(list("&accountingPeriodId=" + ledger.getOpenPeriodId() + "&status="
            + AdjustmentStatus.SUBMITTED.getCode() + "&makerId=" + SubmitManualAdjustmentRequestBuilder.MAKER_ID)
            .assertPage(1, 10, 1)));
        list("&makerId=" + CHECKER_ID).assertSuccess(200).assertPage(1, 10, 0);
    }

    /** 分页稳定：每页 10 条时第 2 页为空；总数随筛选。 */
    @Test
    void test_pagingStable() {
        assertEquals(List.of(historyId, rejectedId, postedId, submittedId), ids(list("&page=1&size=10").assertSuccess(200).assertPage(1, 10, 4)));
        assertEquals(List.of(), ids(list("&page=2&size=10").assertSuccess(200).assertPage(2, 10, 4)));
        list("&size=7").assertInvalidRequest();
    }

    /** 参数错误与账簿不存在：缺账簿、非法状态（含不对外的 DRAFT）、非法编号 400；账簿不存在 LEDGER_BOOK_NOT_FOUND。 */
    @Test
    void test_invalidParameter() {
        get(PATH).assertInvalidRequest();
        list("&status=" + AdjustmentStatus.DRAFT.getCode()).assertInvalidRequest();
        list("&status=UNKNOWN").assertInvalidRequest();
        list("&makerId=abc").assertInvalidRequest();
        list("&accountingPeriodId=0").assertInvalidRequest();
        get(PATH + "?ledgerBookId=123456789").assertRejected(LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND);
    }

    /** 查询本账簿的调整列表，query 以 & 开头追加。 */
    LedgerResponseChecker list(String query) {
        return get(PATH + "?ledgerBookId=" + ledger.getLedgerBookId() + query);
    }

    /** 列表元素的调整主键。 */
    List<Long> ids(List<JsonNode> items) {
        return items.stream().map(item -> Long.parseLong(item.path("manualAdjustmentId").asString())).toList();
    }

    /** 可空字段文本。 */
    String text(JsonNode item, String field) {
        JsonNode node = item.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }
}

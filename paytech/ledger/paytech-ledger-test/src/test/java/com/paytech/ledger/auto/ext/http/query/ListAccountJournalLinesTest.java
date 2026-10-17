package com.paytech.ledger.auto.ext.http.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.api.LedgerBookQueryApi;
import com.paytech.ledger.core.constant.AccountingCodes;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.InstructionOrigin;
import com.paytech.ledger.core.enums.JournalEntryType;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.test.base.AbstractManualAdjustmentApiTest;
import com.paytech.ledger.test.builder.SubmitManualAdjustmentRequestBuilder;
import com.paytech.ledger.test.mocker.local.LedgerMocker;

import tools.jackson.databind.JsonNode;

/**
 * 账户流水：全量历史累计余额、翻页连续、日期过滤不改变累计起点、反向余额、来源追溯与归属校验。
 *
 * @see LedgerBookQueryApi#listAccountJournalLines(String, String, String, String, Integer, Integer)
 */
class ListAccountJournalLinesTest extends AbstractManualAdjustmentApiTest {
    private static final LocalDate HISTORY_END = LocalDate.of(2020, 1, 31);

    private long historyPeriodId;

    @BeforeEach
    void setUpClassMocks() {
        historyPeriodId = ledgerMocker.mockPeriod(ledger.getFiscalCalendarId(), 2020, 1, LocalDate.of(2020, 1, 1),
            HISTORY_END, PeriodStatus.OPEN);
    }

    @Test
    void test_balanceAfterContinuesAcrossPagesAndEndsAtNetBalance() {
        postTwelveDeposits();

        List<JsonNode> first = get(path(bankCashAccountId) + "?size=10").assertSuccess(200).assertPage(1, 10, 12);
        List<JsonNode> second = get(path(bankCashAccountId) + "?page=2&size=10").assertSuccess(200)
            .assertPage(2, 10, 12);

        List<JsonNode> all = new ArrayList<>(first);
        all.addAll(second);
        for (int i = 0; i < all.size(); i++) {
            JsonNode line = all.get(i);
            assertEquals(String.valueOf(i + 1), line.path("journalSequence").asString(), "按序号升序");
            assertEquals((i + 1) + ".00", line.path("balanceAfter").asString(), "第 " + (i + 1) + " 行累计余额");
            assertEquals("1.00", line.path("amount").asString());
        }
        ledgerChecker.assertAccount(bankCashAccountId, "12", "0", "12", 12L);
    }

    @Test
    void test_dateFilterKeepsFullHistoryBalance() {
        postTwelveDeposits();

        List<JsonNode> history = get(path(bankCashAccountId) + "?postingDateFrom=2020-01-01&postingDateTo=2020-01-31")
            .assertSuccess(200).assertPage(1, 10, 2);
        List<JsonNode> recent = get(path(bankCashAccountId) + "?postingDateFrom=2020-02-01").assertSuccess(200)
            .assertPage(1, 10, 10);

        assertEquals(List.of("1.00", "2.00"), history.stream().map(l -> l.path("balanceAfter").asString()).toList());
        assertEquals(HISTORY_END.toString(), history.getFirst().path("postingDate").asString());
        assertEquals("3.00", recent.getFirst().path("balanceAfter").asString(), "过滤后仍从全量历史累计");
        assertEquals("12.00", recent.getLast().path("balanceAfter").asString());
    }

    @Test
    void test_reverseBalanceAndSourceTraceability() {
        var submit = submitted(adjustment().transfer(clientAvailableAccountId, bankCashAccountId, "30"));
        var approval = approve(submit.id("manualAdjustmentId"), CHECKER_ID, submit.text("contentHash")).assertSuccess(200);
        posted(adjustment().transfer(bankCashAccountId, clientAvailableAccountId, "10.25"));

        List<JsonNode> lines = get(path(clientAvailableAccountId)).assertSuccess(200).assertPage(1, 10, 2);

        JsonNode first = lines.getFirst();
        assertAll(() -> assertEquals(EntrySide.DEBIT.getCode(), first.path("entrySide").asString()),
            () -> assertEquals("-30.00", first.path("balanceAfter").asString()),
            () -> assertEquals("-19.75", lines.get(1).path("balanceAfter").asString()),
            () -> assertEquals(JournalEntryType.ADJUSTMENT.getCode(), first.path("entryType").asString()),
            () -> assertEquals(AccountingCodes.MANUAL_ADJUSTMENT_INSTRUCTION, first.path("instructionType").asString()),
            () -> assertEquals(InstructionOrigin.MANUAL.getCode(), first.path("originType").asString()),
            () -> assertEquals(approval.text("journalEntryId"), first.path("journalEntryId").asString()),
            () -> assertEquals(submit.text("adjustmentNo"), first.path("adjustmentNo").asString()),
            () -> assertTrue(first.path("sourceEventKey").isNull()));
    }

    @Test
    void test_emptyAccountReturnsEmptyPage() {
        get(path(capitalAccountId)).assertSuccess(200).assertPage(1, 10, 0);
    }

    @Test
    void test_accountMustBelongToBook() {
        long otherBook = ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE002", "USD",
            LedgerMocker.STANDARD_CHART_ID);
        long foreign = ledgerMocker.mockAccount(otherBook, "CAPITAL", OwnerType.OPERATING_ENTITY, "AE002",
            BalanceBucket.NONE);

        get(path(foreign)).assertBusinessRejected("账户不存在或不属于账簿: 账户 " + foreign);
        get("/ledger-books/v1/123456789/accounts/" + bankCashAccountId + "/journal-lines")
            .assertBusinessRejected("账簿不存在");
    }

    @Test
    void test_invalidDateParameter() {
        get(path(bankCashAccountId) + "?postingDateFrom=2026-02-01&postingDateTo=2026-01-01").assertInvalidRequest()
            .assertDetailContains("postingDateFrom 不能晚于 postingDateTo");
        get(path(bankCashAccountId) + "?postingDateFrom=2026-02-30").assertInvalidRequest()
            .assertDetailContains("不是合法日期");
        get(path(bankCashAccountId) + "?postingDateFrom=20260201").assertStatus(400);
    }

    /** 历史期间 2 笔、当前期间 10 笔，每笔借记银行现金 1。 */
    void postTwelveDeposits() {
        for (int i = 0; i < 2; i++) {
            posted(SubmitManualAdjustmentRequestBuilder.template(ledger.getLedgerBookId(), historyPeriodId)
                .transfer(bankCashAccountId, capitalAccountId, "1"));
        }
        for (int i = 0; i < 10; i++) {
            posted(adjustment().transfer(bankCashAccountId, capitalAccountId, "1"));
        }
    }

    /** 账户流水路径。 */
    String path(long accountId) {
        return "/ledger-books/v1/" + ledger.getLedgerBookId() + "/accounts/" + accountId + "/journal-lines";
    }
}

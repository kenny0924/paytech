package com.paytech.ledger.auto.ext.http.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.ledger.api.LedgerBookQueryApi;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.api.response.JournalEntryDetailResponseBody;
import com.paytech.ledger.core.constant.AccountingCodes;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.InstructionOrigin;
import com.paytech.ledger.core.enums.JournalEntryType;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.test.base.AbstractManualAdjustmentApiTest;
import com.paytech.ledger.test.mocker.local.LedgerMocker;

/**
 * 分录详情：分录头、来源追溯到人工调整、全部分录行借贷相等，以及账簿归属校验。
 *
 * @see LedgerBookQueryApi#getJournalEntry(String, String)
 */
class GetJournalEntryTest extends AbstractManualAdjustmentApiTest {

    @Test
    void test_detailTracesManualAdjustment() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, liabilityAccountId, "42.1"));
        var approval = approve(submit.id("manualAdjustmentId"), CHECKER_ID, submit.text("contentHash")).assertSuccess(200);

        var detail = get(path(approval.text("journalEntryId"))).assertSuccess(200)
            .as(JournalEntryDetailResponseBody.class);

        assertAll(() -> assertEquals(String.valueOf(ledger.getLedgerBookId()), detail.getLedgerBookId()),
            () -> assertEquals(String.valueOf(ledger.getOpenPeriodId()), detail.getAccountingPeriodId()),
            () -> assertEquals("1", detail.getJournalSequence()),
            () -> assertEquals(approval.text("postingDate"), detail.getPostingDate().toString()),
            () -> assertEquals(JournalEntryType.ADJUSTMENT.getCode(), detail.getEntryType()),
            () -> assertNull(detail.getReversalOfEntryId()),
            () -> assertEquals(AccountingCodes.MANUAL_ADJUSTMENT_INSTRUCTION, detail.getInstructionType()),
            () -> assertEquals(InstructionOrigin.MANUAL.getCode(), detail.getOriginType()),
            () -> assertNull(detail.getSourceSystem()),
            () -> assertEquals(submit.text("manualAdjustmentId"), detail.getManualAdjustmentId()),
            () -> assertEquals(submit.text("adjustmentNo"), detail.getAdjustmentNo()),
            () -> assertNull(detail.getAccountPostingId(), "人工指令没有 account 批次"),
            () -> assertNull(detail.getAccountPostingSequence()),
            () -> assertEquals(2, detail.getLines().size()));
        var debit = detail.getLines().get(0);
        var credit = detail.getLines().get(1);
        assertAll(() -> assertEquals(String.valueOf(bankCashAccountId), debit.getAccountId()),
            () -> assertEquals("BANK_CASH", debit.getAccountCode()),
            () -> assertEquals(LedgerOwnerType.CASH_ACCOUNT.getCode(), debit.getOwnerType()),
            () -> assertEquals(EntrySide.DEBIT.getCode(), debit.getEntrySide()),
            () -> assertEquals("42.10", debit.getAmount()),
            () -> assertEquals(LIABILITY_CODE, credit.getAccountCode()),
            () -> assertEquals("AE001", credit.getOwnerId()),
            () -> assertEquals(BalanceBucket.NONE.getCode(), credit.getBalanceBucket()),
            () -> assertEquals(EntrySide.CREDIT.getCode(), credit.getEntrySide()),
            () -> assertNull(debit.getAccountEntryId(), "人工行没有 account 流水"),
            () -> assertEquals(0, new BigDecimal(debit.getAmount()).compareTo(new BigDecimal(credit.getAmount()))));
    }

    @Test
    void test_entryMustBelongToBook() {
        var approval = posted(adjustment().transfer(bankCashAccountId, capitalAccountId, "1"));
        long otherBook = ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE002", "USD",
            LedgerMocker.STANDARD_CHART_ID);
        String entryId = approval.text("journalEntryId");

        get("/ledger-books/v1/" + otherBook + "/journal-entries/" + entryId)
            .assertBusinessRejected("分录不存在或不属于账簿: 分录 " + entryId);
        get(path("123456789")).assertBusinessRejected("分录不存在或不属于账簿");
        get("/ledger-books/v1/123456789/journal-entries/" + entryId).assertRejected(LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND);
    }

    /** 本类账簿的分录详情路径。 */
    String path(String journalEntryId) {
        return "/ledger-books/v1/" + ledger.getLedgerBookId() + "/journal-entries/" + journalEntryId;
    }
}

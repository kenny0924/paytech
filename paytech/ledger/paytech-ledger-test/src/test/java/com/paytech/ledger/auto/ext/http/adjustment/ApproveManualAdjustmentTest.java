package com.paytech.ledger.auto.ext.http.adjustment;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.api.ManualAdjustmentApi;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.api.request.ReviewManualAdjustmentRequestBody;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.dal.dataobject.JournalLine;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.AdjustmentStatus;
import com.paytech.ledger.core.enums.BookStatus;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.InstructionOrigin;
import com.paytech.ledger.core.enums.InstructionStatus;
import com.paytech.ledger.core.enums.JournalEntryType;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.test.base.AbstractManualAdjustmentApiTest;
import com.paytech.ledger.test.builder.SubmitManualAdjustmentRequestBuilder;

/**
 * 批准人工调整并过账：分录与余额、Maker-Checker、重放、锁内门禁回滚和资源锁冲突。
 *
 * @see ManualAdjustmentApi#approveManualAdjustment(String, String, String, ReviewManualAdjustmentRequestBody)
 */
class ApproveManualAdjustmentTest extends AbstractManualAdjustmentApiTest {

    @Autowired
    private RedissonClient redissonClient;

    @Test
    void test_normalFlowPostsBalancedJournal() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100"));
        long id = submit.id("manualAdjustmentId");

        var response = approve(id, CHECKER_ID, submit.text("contentHash")).assertSuccess(200)
            .assertText("status", AdjustmentStatus.POSTED.getCode()).assertText("journalSequence", "1");

        List<JournalEntry> entries = ledgerChecker.journalEntries(ledger.getLedgerBookId());
        JournalEntry entry = entries.getFirst();
        List<JournalLine> lines = ledgerChecker.journalLines(entry.getId());
        assertAll(() -> assertEquals(String.valueOf(entry.getId()), response.text("journalEntryId")),
            () -> assertEquals(JournalEntryType.ADJUSTMENT, entry.getEntryType()),
            () -> assertEquals(ledger.getOpenPeriodId(), entry.getAccountingPeriodId()),
            () -> assertEquals(entry.getPostingDate().toString(), response.text("postingDate")),
            () -> assertEquals(2, lines.size()), () -> assertEquals(EntrySide.DEBIT, lines.get(0).getEntrySide()),
            () -> assertEquals(bankCashAccountId, lines.get(0).getAccountId()));
        ledgerChecker.assertBalancedJournals(ledger.getLedgerBookId(), 1)
            .assertAccount(bankCashAccountId, "100", "0", "100", 1L)
            .assertAccount(capitalAccountId, "0", "100", "100", 1L);
        assertManualInstruction(id, InstructionStatus.POSTED);
        assertEquals(AdjustmentStatus.POSTED, testMapper.selectAdjustment(id).getStatus());
    }

    @Test
    void test_reverseBalanceIsNegativeAndMatchesJournal() {
        var first = submitted(adjustment().transfer(liabilityAccountId, bankCashAccountId, "30"));
        approve(first.id("manualAdjustmentId"), CHECKER_ID, first.text("contentHash")).assertSuccess(200);
        var second = submitted(adjustment().transfer(bankCashAccountId, liabilityAccountId, "10.25"));
        approve(second.id("manualAdjustmentId"), CHECKER_ID, second.text("contentHash")).assertSuccess(200)
            .assertText("journalSequence", "2");

        ledgerChecker.assertBalancedJournals(ledger.getLedgerBookId(), 2)
            .assertAccount(liabilityAccountId, "30", "10.25", "-19.75", 2L)
            .assertAccount(bankCashAccountId, "10.25", "30", "-19.75", 2L);
    }

    /** 提交后改指 Owner 控制科目的调整：批准时同样以专用错误码拒绝，调整保持 SUBMITTED、不写分录。 */
    @Test
    void test_legacyOwnerAccountAdjustmentRejectedOnApprove() {
        long client = ledgerMocker.mockAccount(ledger.getLedgerBookId(), StandardAccountCodes.CLIENT_PAYABLE,
            LedgerOwnerType.OPERATING_ENTITY, ledger.getEntityNo(), BalanceBucket.NONE);
        var submit = submitted(adjustment().transfer(bankCashAccountId, liabilityAccountId, "10"));
        long id = submit.id("manualAdjustmentId");
        testMapper.updateAdjustmentLineAccount(id, 2, client);

        approve(id, CHECKER_ID, submit.text("contentHash")).assertRejectedWithCode(
            LedgerErrorCode.OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN.getCode(), "调整 " + id);

        assertEquals(AdjustmentStatus.SUBMITTED, testMapper.selectAdjustment(id).getStatus());
        ledgerChecker.assertBalancedJournals(ledger.getLedgerBookId(), 0);
    }

    @Test
    void test_sameCheckerReplayReturnsOriginalJournal() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100"));
        long id = submit.id("manualAdjustmentId");
        String journalEntryId = approve(id, CHECKER_ID, submit.text("contentHash")).assertSuccess(200)
            .text("journalEntryId");

        approve(id, CHECKER_ID, submit.text("contentHash")).assertSuccess(200)
            .assertText("journalEntryId", journalEntryId).assertText("status", AdjustmentStatus.POSTED.getCode());

        ledgerChecker.assertBalancedJournals(ledger.getLedgerBookId(), 1);
        assertEquals(1, testMapper.selectInstructions(id).size());
        approve(id, "70003", submit.text("contentHash")).assertRejected(LedgerErrorCode.REVIEW_STATUS_INVALID);
    }

    @Test
    void test_makerCannotApproveOwnAdjustment() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100"));

        approve(submit.id("manualAdjustmentId"), SubmitManualAdjustmentRequestBuilder.MAKER_ID,
            submit.text("contentHash")).assertRejected(LedgerErrorCode.SELF_REVIEW_FORBIDDEN);

        assertNothingPosted(submit.id("manualAdjustmentId"));
    }

    @Test
    void test_contentHashMismatchRejected() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100"));

        approve(submit.id("manualAdjustmentId"), CHECKER_ID, "b".repeat(64))
            .assertRejected(LedgerErrorCode.REVIEW_CONTENT_CHANGED);

        assertNothingPosted(submit.id("manualAdjustmentId"));
    }

    @Test
    void test_inactiveAccountAtApprovalRollsBackEverything() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100"));
        testMapper.updateAccountStatus(capitalAccountId, AccountStatus.INACTIVE.getCode());

        approve(submit.id("manualAdjustmentId"), CHECKER_ID, submit.text("contentHash"))
            .assertBusinessRejected("账户未开放过账: " + capitalAccountId);

        assertNothingPosted(submit.id("manualAdjustmentId"));
        ledgerChecker.assertAccount(bankCashAccountId, "0", "0", "0", 0L);
    }

    @Test
    void test_closedPeriodAtApprovalRollsBackEverything() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100"));
        testMapper.updatePeriodStatus(ledger.getOpenPeriodId(), PeriodStatus.CLOSED.getCode());

        approve(submit.id("manualAdjustmentId"), CHECKER_ID, submit.text("contentHash"))
            .assertBusinessRejected("会计期间未开放");

        assertNothingPosted(submit.id("manualAdjustmentId"));
    }

    @Test
    void test_suspendedBookAtApprovalRollsBackEverything() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100"));
        testMapper.updateBookStatus(ledger.getLedgerBookId(), BookStatus.SUSPENDED.getCode());

        approve(submit.id("manualAdjustmentId"), CHECKER_ID, submit.text("contentHash"))
            .assertBusinessRejected("账簿不存在或未开放: " + ledger.getLedgerBookId());

        assertNothingPosted(submit.id("manualAdjustmentId"));
    }

    @Test
    void test_postingDateClampedIntoPastOpenPeriod() {
        LocalDate start = LocalDate.of(2020, 1, 1);
        long pastPeriod = ledgerMocker.mockPeriod(ledger.getFiscalCalendarId(), 2020, 1, start, start.plusDays(30),
            PeriodStatus.OPEN);
        var submit = submitted(SubmitManualAdjustmentRequestBuilder.template(ledger.getLedgerBookId(), pastPeriod)
            .transfer(bankCashAccountId, capitalAccountId, "1"));

        approve(submit.id("manualAdjustmentId"), CHECKER_ID, submit.text("contentHash")).assertSuccess(200)
            .assertText("postingDate", "2020-01-31");
    }

    @Test
    void test_rejectedAdjustmentCannotBeApproved() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100"));
        long id = submit.id("manualAdjustmentId");
        reject(id, CHECKER_ID, submit.text("contentHash")).assertSuccess(200);

        approve(id, CHECKER_ID, submit.text("contentHash")).assertRejected(LedgerErrorCode.REVIEW_STATUS_INVALID);

        assertEquals(AdjustmentStatus.REJECTED, testMapper.selectAdjustment(id).getStatus());
    }

    @Test
    void test_unknownAdjustmentRejected() {
        approve(123456789L, CHECKER_ID, "a".repeat(64)).assertBusinessRejected("人工调整不存在");
    }

    @Test
    void test_resourceLockHeldReturnsConflict() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100"));
        long id = submit.id("manualAdjustmentId");
        RLock lock = redissonClient.getSpinLock("lock:manual-adjustment:" + id);
        lock.lock();
        try {
            approve(id, CHECKER_ID, submit.text("contentHash")).assertConcurrentRequest();
        } finally {
            lock.unlock();
        }

        assertNothingPosted(id);
    }

    /** 调整仍为 SUBMITTED，且没有任何指令、分录或序号消耗。 */
    void assertNothingPosted(long adjustmentId) {
        assertEquals(AdjustmentStatus.SUBMITTED, testMapper.selectAdjustment(adjustmentId).getStatus());
        assertEquals(0, testMapper.selectInstructions(adjustmentId).size());
        assertEquals(1, testMapper.countApprovals(adjustmentId));
        ledgerChecker.assertBalancedJournals(ledger.getLedgerBookId(), 0);
    }

    /** 调整恰好生成一条指定状态的 MANUAL 指令。 */
    void assertManualInstruction(long adjustmentId, InstructionStatus status) {
        List<AccountingInstruction> instructions = testMapper.selectInstructions(adjustmentId);
        assertEquals(1, instructions.size());
        AccountingInstruction instruction = instructions.getFirst();
        assertAll(() -> assertEquals(InstructionOrigin.MANUAL, instruction.getOriginType()),
            () -> assertEquals(status, instruction.getStatus()),
            () -> assertEquals(testMapper.selectAdjustment(adjustmentId).getContentHash(), instruction.getInputHash()),
            () -> assertEquals(Long.valueOf(CHECKER_ID), instruction.getCheckerId()));
    }
}

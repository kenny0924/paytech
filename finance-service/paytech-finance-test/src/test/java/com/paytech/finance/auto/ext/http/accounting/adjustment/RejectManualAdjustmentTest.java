package com.paytech.finance.auto.ext.http.accounting.adjustment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.paytech.finance.accounting.core.enums.AdjustmentStatus;
import com.paytech.finance.api.accounting.ManualAdjustmentApi;
import com.paytech.finance.api.accounting.request.ReviewManualAdjustmentRequestBody;
import com.paytech.finance.test.base.AbstractManualAdjustmentApiTest;
import com.paytech.finance.test.builder.SubmitManualAdjustmentRequestBuilder;

/**
 * 驳回人工调整：成功不过账、同复核人重放、Maker-Checker 与已处理拒绝。
 *
 * @see ManualAdjustmentApi#rejectManualAdjustment(String, ReviewManualAdjustmentRequestBody)
 */
class RejectManualAdjustmentTest extends AbstractManualAdjustmentApiTest {

    @Test
    void test_normalFlowRejectsWithoutPosting() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100"));
        long id = submit.id("manualAdjustmentId");

        reject(id, CHECKER_ID, submit.text("contentHash")).assertSuccess(200)
            .assertText("status", AdjustmentStatus.REJECTED.getCode()).assertText("journalEntryId", null);

        assertEquals(AdjustmentStatus.REJECTED, testMapper.selectAdjustment(id).getStatus());
        assertEquals(2, testMapper.countApprovals(id));
        assertEquals(0, testMapper.selectInstructions(id).size());
        ledgerChecker.assertBalancedJournals(ledger.getLedgerBookId(), 0);
    }

    @Test
    void test_sameCheckerReplayReturnsCurrentState() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100"));
        long id = submit.id("manualAdjustmentId");
        reject(id, CHECKER_ID, submit.text("contentHash")).assertSuccess(200);

        reject(id, CHECKER_ID, submit.text("contentHash")).assertSuccess(200)
            .assertText("status", AdjustmentStatus.REJECTED.getCode());

        assertEquals(2, testMapper.countApprovals(id));
        reject(id, "70003", submit.text("contentHash")).assertBusinessRejected("人工调整已处理");
    }

    @Test
    void test_makerCannotRejectOwnAdjustment() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100"));

        reject(submit.id("manualAdjustmentId"), SubmitManualAdjustmentRequestBuilder.MAKER_ID,
            submit.text("contentHash")).assertBusinessRejected("复核人不能与编制人相同");
    }

    @Test
    void test_postedAdjustmentCannotBeRejected() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100"));
        long id = submit.id("manualAdjustmentId");
        approve(id, CHECKER_ID, submit.text("contentHash")).assertSuccess(200);

        reject(id, CHECKER_ID, submit.text("contentHash")).assertBusinessRejected("人工调整已处理");

        assertEquals(AdjustmentStatus.POSTED, testMapper.selectAdjustment(id).getStatus());
    }

    @Test
    void test_invalidContentHashFormat() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "100"));

        reject(submit.id("manualAdjustmentId"), CHECKER_ID, "not-a-hash").assertProblem(422, "VALIDATION_ERROR")
            .assertFieldError("approvedContentHash");
    }
}

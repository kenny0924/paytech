package com.paytech.ledger.auto.ext.http.adjustment;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.paytech.ledger.api.ManualAdjustmentApi;
import com.paytech.ledger.api.response.ManualAdjustmentDetailResponseBody;
import com.paytech.ledger.core.enums.AdjustmentStatus;
import com.paytech.ledger.core.enums.ApprovalDecision;
import com.paytech.ledger.core.enums.ApprovalRole;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.test.base.AbstractManualAdjustmentApiTest;
import com.paytech.ledger.test.builder.SubmitManualAdjustmentRequestBuilder;

/**
 * 查询人工调整详情：提交态、过账后的分录与审批记录、金额按币种精度补齐、不存在拒绝。
 *
 * @see ManualAdjustmentApi#getManualAdjustment(String)
 */
class GetManualAdjustmentTest extends AbstractManualAdjustmentApiTest {

    @Test
    void test_submittedDetail() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "7.5"));
        long id = submit.id("manualAdjustmentId");

        var detail = get(PATH + "/" + id).assertSuccess(200).as(ManualAdjustmentDetailResponseBody.class);

        assertAll(() -> assertEquals(String.valueOf(id), detail.getManualAdjustmentId()),
            () -> assertEquals(AdjustmentStatus.SUBMITTED.getCode(), detail.getStatus()),
            () -> assertEquals("7.50", detail.getTotalDebit()), () -> assertEquals("7.50", detail.getTotalCredit()),
            () -> assertEquals(submit.text("contentHash"), detail.getContentHash()),
            () -> assertEquals(2, detail.getLines().size()), () -> assertEquals(1, detail.getLines().get(0).getLineNo()),
            () -> assertEquals(EntrySide.DEBIT.getCode(), detail.getLines().get(0).getEntrySide()),
            () -> assertEquals("7.50", detail.getLines().get(0).getAmount()),
            () -> assertEquals(String.valueOf(capitalAccountId), detail.getLines().get(1).getAccountId()),
            () -> assertEquals(1, detail.getApprovals().size()),
            () -> assertEquals(ApprovalRole.MAKER.getCode(), detail.getApprovals().get(0).getApprovalRole()),
            () -> assertEquals(SubmitManualAdjustmentRequestBuilder.MAKER_ID, detail.getApprovals().get(0).getActorId()),
            () -> assertNull(detail.getJournalEntryId()), () -> assertNull(detail.getPostingDate()));
    }

    @Test
    void test_postedDetailIncludesJournalAndChecker() {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "7.5"));
        long id = submit.id("manualAdjustmentId");
        var approval = approve(id, CHECKER_ID, submit.text("contentHash")).assertSuccess(200);

        var detail = get(PATH + "/" + id).assertSuccess(200).as(ManualAdjustmentDetailResponseBody.class);

        assertAll(() -> assertEquals(AdjustmentStatus.POSTED.getCode(), detail.getStatus()),
            () -> assertEquals(approval.text("journalEntryId"), detail.getJournalEntryId()),
            () -> assertEquals("1", detail.getJournalSequence()),
            () -> assertEquals(approval.text("postingDate"), detail.getPostingDate().toString()),
            () -> assertEquals(2, detail.getApprovals().size()),
            () -> assertEquals(ApprovalRole.CHECKER.getCode(), detail.getApprovals().get(1).getApprovalRole()),
            () -> assertEquals(CHECKER_ID, detail.getApprovals().get(1).getActorId()),
            () -> assertEquals(ApprovalDecision.APPROVED.getCode(), detail.getApprovals().get(1).getDecision()));
    }

    @Test
    void test_noDataFound() {
        get(PATH + "/123456789").assertBusinessRejected("人工调整不存在");
    }
}

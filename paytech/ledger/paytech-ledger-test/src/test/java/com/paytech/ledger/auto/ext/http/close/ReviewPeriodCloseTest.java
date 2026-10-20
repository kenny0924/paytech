package com.paytech.ledger.auto.ext.http.close;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.core.enums.ApprovalDecision;
import com.paytech.ledger.core.enums.ApprovalRole;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.test.base.AbstractPeriodCloseApiTest;
import com.paytech.ledger.test.checker.LedgerResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 关账复核：批准关闭期间、驳回回到 OPEN、Maker-Checker 与摘要校验、重复与非待审批状态拒绝、关闭后过账被拒。
 *
 * @see com.paytech.ledger.api.PeriodCloseApi#approvePeriodClose(String, String, String, String, com.paytech.ledger.api.request.ReviewPeriodCloseRequestBody)
 * @see com.paytech.ledger.api.PeriodCloseApi#rejectPeriodClose(String, String, String, String, com.paytech.ledger.api.request.ReviewPeriodCloseRequestBody)
 */
class ReviewPeriodCloseTest extends AbstractPeriodCloseApiTest {

    @Test
    void test_approveClosesPeriod() {
        LedgerResponseChecker started = startedClose();

        approveClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, started.text("closeHash")).assertSuccess(200)
            .assertText("status", "COMPLETED").assertText("checkerId", CLOSE_CHECKER_ID)
            .assertText("closeHash", started.text("closeHash"));

        assertEquals(PeriodStatus.CLOSED.getCode(), testMapper.selectPeriodStatus(ledger.getOpenPeriodId()));
        JsonNode approvals = closeDetail(ledger.getOpenPeriodId(), "1").assertSuccess(200).json().path("approvals");
        assertEquals(2, approvals.size());
        JsonNode checker = approvals.get(1);
        assertAll(() -> assertEquals(ApprovalRole.MAKER.getCode(), approvals.get(0).path("approvalRole").asString()),
            () -> assertEquals(CLOSE_MAKER_ID, approvals.get(0).path("actorId").asString()),
            () -> assertEquals(ApprovalRole.CHECKER.getCode(), checker.path("approvalRole").asString()),
            () -> assertEquals(ApprovalDecision.APPROVED.getCode(), checker.path("decision").asString()),
            () -> assertEquals(started.text("closeHash"), checker.path("approvedCloseHash").asString()));
    }

    @Test
    void test_rejectReturnsPeriodToOpen() {
        LedgerResponseChecker started = startedClose();

        rejectClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, started.text("closeHash")).assertSuccess(200)
            .assertText("status", "REJECTED");

        assertEquals(PeriodStatus.OPEN.getCode(), testMapper.selectPeriodStatus(ledger.getOpenPeriodId()));
        JsonNode checker = closeDetail(ledger.getOpenPeriodId(), "1").assertSuccess(200).json().path("approvals").get(1);
        assertEquals(ApprovalDecision.REJECTED.getCode(), checker.path("decision").asString());
        startClose().assertSuccess(201).assertText("closeNo", "2").assertText("status", "PENDING_CHECK");
    }

    @Test
    void test_makerCannotReviewOwnClose() {
        LedgerResponseChecker started = startedClose();

        approveClose(ledger.getOpenPeriodId(), "1", CLOSE_MAKER_ID, started.text("closeHash"))
            .assertRejected(LedgerErrorCode.SELF_REVIEW_FORBIDDEN);

        assertEquals(PeriodStatus.CLOSING.getCode(), testMapper.selectPeriodStatus(ledger.getOpenPeriodId()));
    }

    @Test
    void test_closeHashMismatchRejected() {
        LedgerResponseChecker started = startedClose();

        approveClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, "c".repeat(64))
            .assertRejected(LedgerErrorCode.REVIEW_CONTENT_CHANGED);
        rejectClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, "c".repeat(64))
            .assertRejected(LedgerErrorCode.REVIEW_CONTENT_CHANGED);
    }

    @Test
    void test_duplicateApprovalRejected() {
        LedgerResponseChecker started = startedClose();
        approveClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, started.text("closeHash")).assertSuccess(200);

        approveClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, started.text("closeHash"))
            .assertRejected(LedgerErrorCode.REVIEW_STATUS_INVALID);
    }

    @Test
    void test_failedCloseCannotBeApproved() {
        mockBillingUnpublished();
        LedgerResponseChecker failed = startClose().assertSuccess(201).assertText("status", "FAILED");

        approveClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, "a".repeat(64))
            .assertRejected(LedgerErrorCode.REVIEW_STATUS_INVALID);
    }

    @Test
    void test_closedPeriodRejectsPosting() {
        var pending = submitPendingAdjustment();
        LedgerResponseChecker started = startedClose();
        approveClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, started.text("closeHash")).assertSuccess(200);

        approvePending(pending).assertBusinessRejected("会计期间未开放");
    }

    @Test
    void test_unknownCloseRejected() {
        approveClose(ledger.getOpenPeriodId(), "9", CLOSE_CHECKER_ID, "a".repeat(64))
            .assertBusinessRejected("关账流程不存在: 期间 " + ledger.getOpenPeriodId() + ", 序号 9");
    }

    /** 准备一笔收入并发起关账，断言进入待审批。 */
    LedgerResponseChecker startedClose() {
        postRevenue("100");
        return startClose().assertSuccess(201).assertText("status", "PENDING_CHECK");
    }
}

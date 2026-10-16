package com.paytech.finance.auto.ext.http.accounting.close;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.paytech.finance.accounting.core.enums.ApprovalDecision;
import com.paytech.finance.accounting.core.enums.ApprovalRole;
import com.paytech.finance.accounting.core.enums.PeriodStatus;
import com.paytech.finance.test.base.AbstractPeriodCloseApiTest;
import com.paytech.finance.test.checker.FinanceResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 关账复核：批准关闭期间、驳回回到 OPEN、Maker-Checker 与摘要校验、重复与非待审批状态拒绝、关闭后过账被拒。
 *
 * @see com.paytech.finance.api.accounting.PeriodCloseApi#approvePeriodClose(String, String, com.paytech.finance.api.accounting.request.ReviewPeriodCloseRequestBody)
 * @see com.paytech.finance.api.accounting.PeriodCloseApi#rejectPeriodClose(String, String, com.paytech.finance.api.accounting.request.ReviewPeriodCloseRequestBody)
 */
class ReviewPeriodCloseTest extends AbstractPeriodCloseApiTest {

    @Test
    void test_approveClosesPeriod() {
        FinanceResponseChecker started = startedClose();

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
        FinanceResponseChecker started = startedClose();

        rejectClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, started.text("closeHash")).assertSuccess(200)
            .assertText("status", "REJECTED");

        assertEquals(PeriodStatus.OPEN.getCode(), testMapper.selectPeriodStatus(ledger.getOpenPeriodId()));
        JsonNode checker = closeDetail(ledger.getOpenPeriodId(), "1").assertSuccess(200).json().path("approvals").get(1);
        assertEquals(ApprovalDecision.REJECTED.getCode(), checker.path("decision").asString());
        startClose().assertSuccess(201).assertText("closeNo", "2").assertText("status", "PENDING_CHECK");
    }

    @Test
    void test_makerCannotReviewOwnClose() {
        FinanceResponseChecker started = startedClose();

        approveClose(ledger.getOpenPeriodId(), "1", CLOSE_MAKER_ID, started.text("closeHash"))
            .assertBusinessRejected("复核人不能与编制人相同: 关账 " + started.text("periodCloseId") + ", 用户 "
                + CLOSE_MAKER_ID);

        assertEquals(PeriodStatus.CLOSING.getCode(), testMapper.selectPeriodStatus(ledger.getOpenPeriodId()));
    }

    @Test
    void test_closeHashMismatchRejected() {
        FinanceResponseChecker started = startedClose();

        approveClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, "c".repeat(64))
            .assertBusinessRejected("复核确认的关账摘要与流程不一致: " + started.text("periodCloseId"));
        rejectClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, "c".repeat(64))
            .assertBusinessRejected("复核确认的关账摘要与流程不一致: " + started.text("periodCloseId"));
    }

    @Test
    void test_duplicateApprovalRejected() {
        FinanceResponseChecker started = startedClose();
        approveClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, started.text("closeHash")).assertSuccess(200);

        approveClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, started.text("closeHash"))
            .assertBusinessRejected("关账流程不在待审批状态: " + started.text("periodCloseId") + ", 状态 COMPLETED");
    }

    @Test
    void test_failedCloseCannotBeApproved() {
        postBankCash("10");
        FinanceResponseChecker failed = startClose().assertSuccess(201).assertText("status", "FAILED");

        approveClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, "a".repeat(64))
            .assertBusinessRejected("关账流程不在待审批状态: " + failed.text("periodCloseId") + ", 状态 FAILED");
    }

    @Test
    void test_closedPeriodRejectsPosting() {
        var pending = submitPendingAdjustment();
        FinanceResponseChecker started = startedClose();
        approveClose(ledger.getOpenPeriodId(), "1", CLOSE_CHECKER_ID, started.text("closeHash")).assertSuccess(200);

        approvePending(pending).assertBusinessRejected("会计期间未开放");
    }

    @Test
    void test_unknownCloseRejected() {
        approveClose(ledger.getOpenPeriodId(), "9", CLOSE_CHECKER_ID, "a".repeat(64))
            .assertBusinessRejected("关账流程不存在: 期间 " + ledger.getOpenPeriodId() + ", 序号 9");
    }

    /** 准备一笔收入并发起关账，断言进入待审批。 */
    FinanceResponseChecker startedClose() {
        postRevenue("100");
        return startClose().assertSuccess(201).assertText("status", "PENDING_CHECK");
    }
}

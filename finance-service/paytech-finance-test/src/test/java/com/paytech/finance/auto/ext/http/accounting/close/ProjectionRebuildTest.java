package com.paytech.finance.auto.ext.http.accounting.close;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.finance.api.accounting.request.StartProjectionRebuildRequestBody;
import com.paytech.finance.api.accounting.request.ApproveProjectionRebuildRequestBody;
import com.paytech.finance.test.base.AbstractPeriodCloseApiTest;
import com.paytech.finance.test.checker.FinanceResponseChecker;

/**
 * 试算异常投影重建：Maker 发起、Checker 批准后从 Journal 重算账户投影并解决异常，解除关账阻断；Maker-Checker 与状态校验。
 *
 * @see com.paytech.finance.api.accounting.TrialExceptionApi#startProjectionRebuild(String, com.paytech.finance.api.accounting.request.StartProjectionRebuildRequestBody)
 * @see com.paytech.finance.api.accounting.TrialExceptionApi#approveProjectionRebuild(String, com.paytech.finance.api.accounting.request.ApproveProjectionRebuildRequestBody)
 */
class ProjectionRebuildTest extends AbstractPeriodCloseApiTest {
    private static final String REBUILD_MAKER_ID = "90001";

    private static final String REBUILD_CHECKER_ID = "90002";

    @Test
    void test_rebuildResolvesExceptionAndUnblocksClose() {
        postRevenue("100");
        testMapper.updateAccountNetBalance(capitalAccountId, "999");
        startClose().assertSuccess(201).assertText("status", "FAILED").assertText("failedStepType", "RESOLVE_EXCEPTIONS");
        long exceptionId = singleCapitalException();

        requestRebuild(exceptionId, REBUILD_MAKER_ID).assertSuccess(200).assertText("status", "UNDER_REVIEW")
            .assertText("resolutionType", "PROJECTION_REBUILD").assertText("resolutionMakerId", REBUILD_MAKER_ID);
        var approved = approveRebuild(exceptionId, REBUILD_CHECKER_ID).assertSuccess(200);

        assertAll(() -> assertEquals("RESOLVED", approved.text("status")),
            () -> assertEquals(String.valueOf(capitalAccountId), approved.text("resolutionRefId")),
            () -> assertEquals(REBUILD_CHECKER_ID, approved.text("resolutionCheckerId")),
            () -> assertTrue(approved.text("resolutionContentHash").matches("[0-9a-f]{64}")),
            () -> assertEquals("RESOLVED", testMapper.selectExceptionStatus(exceptionId)));
        ledgerChecker.assertProjectionMatchesJournal(capitalAccountId);
        startClose().assertSuccess(201).assertText("status", "PENDING_CHECK").assertText("closeNo", "2");
    }

    @Test
    void test_makerCannotApproveOwnRebuild() {
        long exceptionId = tamperedException();
        requestRebuild(exceptionId, REBUILD_MAKER_ID).assertSuccess(200);

        approveRebuild(exceptionId, REBUILD_MAKER_ID)
            .assertBusinessRejected("复核人不能与编制人相同: 异常 " + exceptionId + ", 用户 " + REBUILD_MAKER_ID);
        assertEquals("UNDER_REVIEW", testMapper.selectExceptionStatus(exceptionId));
    }

    @Test
    void test_illegalStateRejected() {
        long exceptionId = tamperedException();

        approveRebuild(exceptionId, REBUILD_CHECKER_ID)
            .assertBusinessRejected("试算异常状态不允许该操作: " + exceptionId + ", 状态 OPEN");
        requestRebuild(exceptionId, REBUILD_MAKER_ID).assertSuccess(200);
        requestRebuild(exceptionId, REBUILD_MAKER_ID)
            .assertBusinessRejected("试算异常状态不允许该操作: " + exceptionId + ", 状态 UNDER_REVIEW");
    }

    @Test
    void test_unknownExceptionRejected() {
        requestRebuild(123456789L, REBUILD_MAKER_ID).assertBusinessRejected("试算异常不存在: 123456789");
        approveRebuild(123456789L, REBUILD_CHECKER_ID).assertBusinessRejected("试算异常不存在: 123456789");
    }

    /** 过账后篡改实收资本投影并直接试算，返回产生的异常主键。 */
    long tamperedException() {
        postRevenue("100");
        testMapper.updateAccountNetBalance(capitalAccountId, "999");
        post("/ledger-books/v1/" + ledger.getLedgerBookId() + "/periods/" + ledger.getOpenPeriodId()
            + "/trial-balances", null).assertSuccess(201).assertText("status", "FAILED");
        return singleCapitalException();
    }

    /** 读取引用实收资本账户的唯一异常。 */
    long singleCapitalException() {
        List<Long> ids = testMapper.selectAccountExceptionIds(capitalAccountId);
        assertEquals(1, ids.size(), "应只有一条引用实收资本账户的异常");
        return ids.getFirst();
    }

    /** 发起投影重建。 */
    FinanceResponseChecker requestRebuild(long exceptionId, String makerId) {
        return post("/trial-balance-exceptions/v1/" + exceptionId + "/projection-rebuild", startRebuildBody(makerId));
    }

    /** 批准投影重建。 */
    FinanceResponseChecker approveRebuild(long exceptionId, String checkerId) {
        return post("/trial-balance-exceptions/v1/" + exceptionId + "/projection-rebuild/approve",
            approveRebuildBody(checkerId));
    }

    /** 发起投影重建请求体。 */
    StartProjectionRebuildRequestBody startRebuildBody(String makerId) {
        StartProjectionRebuildRequestBody body = new StartProjectionRebuildRequestBody();
        body.setMakerId(makerId);
        return body;
    }

    /** 批准投影重建请求体。 */
    ApproveProjectionRebuildRequestBody approveRebuildBody(String checkerId) {
        ApproveProjectionRebuildRequestBody body = new ApproveProjectionRebuildRequestBody();
        body.setCheckerId(checkerId);
        return body;
    }
}

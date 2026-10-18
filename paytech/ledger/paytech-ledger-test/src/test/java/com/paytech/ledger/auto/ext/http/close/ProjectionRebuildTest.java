package com.paytech.ledger.auto.ext.http.close;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.ledger.api.request.ApproveProjectionRebuildRequestBody;
import com.paytech.ledger.api.request.StartProjectionRebuildRequestBody;
import com.paytech.ledger.test.base.AbstractPeriodCloseApiTest;
import com.paytech.ledger.test.checker.LedgerResponseChecker;

/**
 * 试算异常投影重建的 ledger 侧处置（design/04 §4 第 5 行）：Maker 发起、Checker 批准后只写 t_trial_balance_exception，冻结引用账户按分录聚合的值，
 * 不写分录、不写余额；Maker-Checker 与状态校验。
 * <li>第 11b 步起 ledger 不保存余额投影，试算不再产生账户投影异常；用例以测试 Mapper 写入第 11b 步前遗留的 GENERAL_LEDGER_MISMATCH/ACCOUNT
 * 异常。Owner 余额的重算由 account 的 {@code POST /balances/v1/books/{bookId}/rebuild} 承担（account-test 覆盖）。</li>
 *
 * @see com.paytech.ledger.api.TrialExceptionApi#startProjectionRebuild(String, com.paytech.ledger.api.request.StartProjectionRebuildRequestBody)
 * @see com.paytech.ledger.api.TrialExceptionApi#approveProjectionRebuild(String, com.paytech.ledger.api.request.ApproveProjectionRebuildRequestBody)
 */
class ProjectionRebuildTest extends AbstractPeriodCloseApiTest {
    private static final String REBUILD_MAKER_ID = "90001";

    private static final String REBUILD_CHECKER_ID = "90002";

    @Autowired
    private IdGenerator idGenerator;

    @Test
    void test_rebuildResolvesLegacyExceptionWithoutJournal() {
        long exceptionId = legacyException();
        int journals = testMapper.countAllJournalEntries();

        requestRebuild(exceptionId, REBUILD_MAKER_ID).assertSuccess(200).assertText("status", "UNDER_REVIEW")
            .assertText("resolutionType", "PROJECTION_REBUILD").assertText("resolutionMakerId", REBUILD_MAKER_ID);
        var approved = approveRebuild(exceptionId, REBUILD_CHECKER_ID).assertSuccess(200);

        assertAll(() -> assertEquals("RESOLVED", approved.text("status")),
            () -> assertEquals(String.valueOf(capitalAccountId), approved.text("resolutionRefId")),
            () -> assertEquals(REBUILD_CHECKER_ID, approved.text("resolutionCheckerId")),
            () -> assertTrue(approved.text("resolutionContentHash").matches("[0-9a-f]{64}")),
            () -> assertEquals("RESOLVED", testMapper.selectExceptionStatus(exceptionId)),
            () -> assertEquals(journals, testMapper.countAllJournalEntries(), "处置不得写分录"));
        ledgerChecker.assertAccount(capitalAccountId, "100", "0", "-100", 1L);
        startClose().assertSuccess(201).assertText("status", "PENDING_CHECK").assertText("closeNo", "1");
    }

    @Test
    void test_makerCannotApproveOwnRebuild() {
        long exceptionId = legacyException();
        requestRebuild(exceptionId, REBUILD_MAKER_ID).assertSuccess(200);

        approveRebuild(exceptionId, REBUILD_MAKER_ID)
            .assertBusinessRejected("复核人不能与编制人相同: 异常 " + exceptionId + ", 用户 " + REBUILD_MAKER_ID);
        assertEquals("UNDER_REVIEW", testMapper.selectExceptionStatus(exceptionId));
    }

    @Test
    void test_illegalStateRejected() {
        long exceptionId = legacyException();

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

    /** 过账收入并成功试算后，在该试算下写入一条引用实收资本账户的存量 OPEN 异常，返回其主键。 */
    long legacyException() {
        postRevenue("100");
        post("/ledger-books/v1/" + ledger.getLedgerBookId() + "/periods/" + ledger.getOpenPeriodId()
            + "/trial-balances", null).assertSuccess(201).assertText("status", "SUCCEEDED");
        long runId = testMapper.selectLatestTrialRunId(ledger.getLedgerBookId());
        testMapper.insertLegacyAccountException(idGenerator.nextId(), runId, capitalAccountId, "899.00");
        List<Long> ids = testMapper.selectAccountExceptionIds(capitalAccountId);
        assertEquals(1, ids.size(), "应只有一条引用实收资本账户的异常");
        return ids.getFirst();
    }

    /** 发起投影重建。 */
    LedgerResponseChecker requestRebuild(long exceptionId, String makerId) {
        return post("/trial-balance-exceptions/v1/" + exceptionId + "/projection-rebuild", startRebuildBody(makerId));
    }

    /** 批准投影重建。 */
    LedgerResponseChecker approveRebuild(long exceptionId, String checkerId) {
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

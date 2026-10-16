package com.paytech.finance.auto.ext.http.accounting.close;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.finance.accounting.core.dal.dataobject.JournalEntry;
import com.paytech.finance.accounting.core.enums.JournalEntryType;
import com.paytech.finance.accounting.core.enums.LedgerEntityType;
import com.paytech.finance.accounting.core.enums.OwnerType;
import com.paytech.finance.accounting.core.enums.PeriodStatus;
import com.paytech.finance.test.base.AbstractPeriodCloseApiTest;
import com.paytech.finance.test.mocker.local.LedgerMocker;
import com.paytech.foundation.id.IdGenerator;

import tools.jackson.databind.JsonNode;

/**
 * 发起期间关账：7 个固定步骤、损益结转、最终试算与 close_hash 冻结，以及各步骤失败回滚与前置校验。
 *
 * @see com.paytech.finance.api.accounting.PeriodCloseApi#startPeriodClose(String, com.paytech.finance.api.accounting.request.StartPeriodCloseRequestBody)
 */
class StartPeriodCloseTest extends AbstractPeriodCloseApiTest {
    private static final List<String> STEP_TYPES = List.of("LOCK_SEQUENCE", "CHECK_ASYNC_AND_JOURNAL",
        "RECONCILE_ACCOUNT_BALANCES", "GENERATE_TRIAL_BALANCE", "RESOLVE_EXCEPTIONS", "POST_PROFIT_CLOSE",
        "FINAL_TRIAL_BALANCE");

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private RedissonClient redissonClient;

    @Test
    void test_normalFlowClosesProfitAndFreezesHash() {
        postRevenue("300");
        postExpense("120");

        var response = startClose().assertSuccess(201).assertText("status", "PENDING_CHECK")
            .assertText("closeType", "CLOSE").assertText("closeNo", "1");

        assertTrue(response.text("closeHash").matches("[0-9a-f]{64}"));
        assertEquals(null, response.text("failedStepType"));
        assertEquals(PeriodStatus.CLOSING.getCode(), testMapper.selectPeriodStatus(ledger.getOpenPeriodId()));
        Long resultAccountId = testMapper.selectAccountId(ledger.getLedgerBookId(), CURRENT_RESULT_CODE,
            OwnerType.OPERATING_ENTITY.getCode(), ledger.getEntityNo());
        assertNotNull(resultAccountId, "结转应自动创建账簿主体的本期损益账户");
        assertAll(() -> assertEquals(0, BigDecimal.ZERO.compareTo(testMapper.selectAccountNetBalance(revenueAccountId))),
            () -> assertEquals(0, BigDecimal.ZERO.compareTo(testMapper.selectAccountNetBalance(expenseAccountId))),
            () -> assertEquals(0, new BigDecimal("180").compareTo(testMapper.selectAccountNetBalance(resultAccountId))));
        ledgerChecker.assertProjectionMatchesJournal(revenueAccountId).assertProjectionMatchesJournal(expenseAccountId)
            .assertProjectionMatchesJournal(resultAccountId);

        JsonNode detail = closeDetail(ledger.getOpenPeriodId(), "1").assertSuccess(200).json();
        assertStepsSucceeded(detail);
        JsonNode book = detail.path("books").get(0);
        assertEquals(1, detail.path("books").size());
        assertEquals("FINALIZED", book.path("status").asString());
        assertTrue(!book.path("finalTrialRunId").isNull());
        JournalEntry closing = testMapper.selectJournalEntryByInstruction(
            Long.parseLong(book.path("postingInstructionId").asString()));
        assertAll(() -> assertEquals(JournalEntryType.CLOSING, closing.getEntryType()),
            () -> assertEquals(LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()), closing.getPostingDate()),
            () -> assertEquals(String.valueOf(closing.getJournalSequence()), book.path("asOfSequence").asString()));
    }

    @Test
    void test_bookWithoutProfitHasNoClosingInstruction() {
        ledgerMocker.mockCurrency("AED", 2);
        long aedBook = ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, ledger.getEntityNo(), "AED",
            LedgerMocker.STANDARD_CHART_ID);
        postRevenue("50");

        startClose().assertSuccess(201).assertText("status", "PENDING_CHECK");

        JsonNode books = closeDetail(ledger.getOpenPeriodId(), "1").assertSuccess(200).json().path("books");
        assertEquals(2, books.size());
        for (JsonNode book : books) {
            assertEquals("FINALIZED", book.path("status").asString());
            boolean isAed = book.path("ledgerBookId").asString().equals(String.valueOf(aedBook));
            assertEquals(isAed, book.path("postingInstructionId").isNull(), "无收入费用的账簿不生成结转指令");
        }
    }

    @Test
    void test_closingPeriodRejectsPosting() {
        var pending = submitPendingAdjustment();
        postRevenue("10");

        startClose().assertSuccess(201).assertText("status", "PENDING_CHECK");

        approvePending(pending).assertBusinessRejected("会计期间未开放");
        startClose().assertBusinessRejected("只有 OPEN 期间可以发起关账: " + ledger.getOpenPeriodId() + ", 状态 "
            + PeriodStatus.CLOSING.getCode());
    }

    @Test
    void test_closeLockHeldReturnsConflict() {
        RLock lock = redissonClient.getSpinLock("lock:period-close:" + ledger.getOpenPeriodId());
        lock.lock();
        try {
            startClose().assertConcurrentRequest();
        } finally {
            lock.unlock();
        }

        assertEquals(0, testMapper.countPeriodCloses(ledger.getOpenPeriodId()));
        assertEquals(PeriodStatus.OPEN.getCode(), testMapper.selectPeriodStatus(ledger.getOpenPeriodId()));
    }

    @Test
    void test_integrityCheckFailureRollsPeriodBack() {
        postRevenue("10");
        testMapper.insertPendingInstruction(idGenerator.nextId(), ledger.getLedgerBookId());

        assertFailedAt("CHECK_ASYNC_AND_JOURNAL", "1");

        assertFailedAt("CHECK_ASYNC_AND_JOURNAL", "2");
    }

    @Test
    void test_nonZeroCashBalanceFailsReconciliation() {
        postBankCash("10");

        assertFailedAt("RECONCILE_ACCOUNT_BALANCES", "1");
    }

    @Test
    void test_ledgerMismatchFailsExceptionStepBeforeProfitClose() {
        postRevenue("100");
        testMapper.updateAccountNetBalance(capitalAccountId, "999");

        assertFailedAt("RESOLVE_EXCEPTIONS", "1");

        assertEquals(0, testMapper.countInstructions(ledger.getLedgerBookId(), "PERIOD_PROFIT_CLOSE"));
    }

    @Test
    void test_earlierPeriodMustBeClosed() {
        long earlier = ledgerMocker.mockPeriod(ledger.getFiscalCalendarId(), 2020, 1, LocalDate.of(2020, 1, 1),
            LocalDate.of(2020, 1, 31), PeriodStatus.OPEN);

        startClose().assertBusinessRejected("前序会计期间尚未关闭: " + earlier);
        assertEquals(0, testMapper.countPeriodCloses(ledger.getOpenPeriodId()));

        testMapper.updatePeriodStatus(earlier, PeriodStatus.CLOSED.getCode());
        startClose().assertSuccess(201).assertText("status", "PENDING_CHECK");
    }

    @Test
    void test_invalidPeriodRejected() {
        startClose(123456789L).assertBusinessRejected("会计期间不存在: 123456789");

        long future = ledgerMocker.mockPeriod(ledger.getFiscalCalendarId(), 2099, 1, LocalDate.of(2099, 1, 1),
            LocalDate.of(2099, 1, 31), PeriodStatus.FUTURE);
        startClose(future).assertBusinessRejected("只有 OPEN 期间可以发起关账: " + future + ", 状态 "
            + PeriodStatus.FUTURE.getCode());
    }

    /** 发起关账，断言在指定步骤失败、期间回到 OPEN、活动槽已释放（编号连续）。 */
    void assertFailedAt(String stepType, String closeNo) {
        startClose().assertSuccess(201).assertText("status", "FAILED").assertText("failedStepType", stepType)
            .assertText("closeNo", closeNo);
        assertEquals(PeriodStatus.OPEN.getCode(), testMapper.selectPeriodStatus(ledger.getOpenPeriodId()));
        JsonNode steps = closeDetail(ledger.getOpenPeriodId(), closeNo).assertSuccess(200).json().path("steps");
        for (JsonNode step : steps) {
            if (step.path("stepType").asString().equals(stepType)) {
                assertEquals("FAILED", step.path("status").asString());
                assertTrue(step.path("resultHash").asString().matches("[0-9a-f]{64}"), "失败步骤也应保留结果摘要");
            }
        }
    }

    /** 7 个步骤按顺序全部成功且各有结果摘要。 */
    void assertStepsSucceeded(JsonNode detail) {
        List<String> types = new ArrayList<>();
        for (JsonNode step : detail.path("steps")) {
            types.add(step.path("stepType").asString());
            assertEquals("SUCCEEDED", step.path("status").asString(), step.path("stepType").asString());
            assertTrue(step.path("resultHash").asString().matches("[0-9a-f]{64}"));
            assertTrue(!step.path("completedTime").isNull());
        }
        assertEquals(STEP_TYPES, types);
    }
}

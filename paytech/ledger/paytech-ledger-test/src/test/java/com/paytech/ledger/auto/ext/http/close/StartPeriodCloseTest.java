package com.paytech.ledger.auto.ext.http.close;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.JournalEntryType;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.test.base.AbstractPeriodCloseApiTest;
import com.paytech.ledger.test.mocker.external.AccountPostingMocker;
import com.paytech.ledger.test.mocker.external.BillingFactMocker;
import com.paytech.ledger.test.mocker.local.LedgerMocker;

import tools.jackson.databind.JsonNode;

/**
 * 发起期间关账：7 个固定步骤、损益结转、最终试算与 close_hash 冻结，以及各步骤失败回滚与前置校验。
 *
 * @see com.paytech.ledger.api.PeriodCloseApi#startPeriodClose(String, String, String)
 */
class StartPeriodCloseTest extends AbstractPeriodCloseApiTest {
    private static final List<String> STEP_TYPES = List.of("LOCK_SEQUENCE", "CHECK_ASYNC_AND_JOURNAL",
        "CHECK_ACCOUNT_POSTING_PROGRESS", "GENERATE_TRIAL_BALANCE", "RESOLVE_EXCEPTIONS", "POST_PROFIT_CLOSE",
        "FINAL_TRIAL_BALANCE");

    /** 入账进度检查步骤。 */
    private static final String STEP_3 = "CHECK_ACCOUNT_POSTING_PROGRESS";

    /** billing / account 账簿主体类型：经营主体。 */
    private static final String OE = "OPERATING_ENTITY";

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
            LedgerOwnerType.OPERATING_ENTITY.getCode(), ledger.getEntityNo());
        assertNotNull(resultAccountId, "结转应自动创建账簿主体的本期损益账户");
        assertEquals(ledger.getEntityNo() + "-USD-31030101-0001", testMapper.selectAccount(resultAccountId).getAccountNo(),
            "关账建户的账户号");
        assertAll(() -> assertEquals(0, BigDecimal.ZERO.compareTo(testMapper.selectAccountNetBalance(revenueAccountId))),
            () -> assertEquals(0, BigDecimal.ZERO.compareTo(testMapper.selectAccountNetBalance(expenseAccountId))),
            () -> assertEquals(0, new BigDecimal("180").compareTo(testMapper.selectAccountNetBalance(resultAccountId))));

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

    /** 本期损益科目停用后关账需要新建损益账户：以 CHART_ACCOUNT_INACTIVE 失败，期间回到 OPEN，不建户。 */
    @Test
    void test_inactiveResultAccountRejectsClose() {
        postRevenue("300");
        testMapper.updateStandardChartAccountStatus(CURRENT_RESULT_CODE, ChartAccountStatus.INACTIVE.getCode());

        startClose().assertRejectedWithCode(LedgerSetupErrorCode.CHART_ACCOUNT_INACTIVE.getCode(), CURRENT_RESULT_CODE);

        assertEquals(PeriodStatus.OPEN.getCode(), testMapper.selectPeriodStatus(ledger.getOpenPeriodId()));
        assertNull(testMapper.selectAccountId(ledger.getLedgerBookId(), CURRENT_RESULT_CODE,
            LedgerOwnerType.OPERATING_ENTITY.getCode(), ledger.getEntityNo()), "不应残留损益账户");
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
        startClose().assertRejected(LedgerErrorCode.PERIOD_NOT_OPEN);
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

    /** 银行现金余额非零不再阻断关账（原余额核对步骤已由入账进度检查替代）。 */
    @Test
    void test_nonZeroCashBalanceNoLongerBlocks() {
        postBankCash("10");

        startClose().assertSuccess(201).assertText("status", "PENDING_CHECK");
    }

    @Test
    void test_ledgerMismatchFailsExceptionStepBeforeProfitClose() {
        postUnbalancedPriorPeriod();
        postRevenue("100");

        assertFailedAt("RESOLVE_EXCEPTIONS", "1");

        assertEquals(0, testMapper.countInstructions(ledger.getLedgerBookId(), "PERIOD_PROFIT_CLOSE"));
    }

    /** 步骤 3 段 ①：billing 仍有已批准未发布的事实时失败关闭。 */
    @Test
    void test_billingUnpublishedFactsFail() {
        postRevenue("10");
        billingFacts.mockProgress(OE, ledger.getEntityNo(), ledger.getCurrency(), 1, 0, null);

        assertFailedAt(STEP_3, "1");
    }

    /** 步骤 3 段 ①：account 已应用事实数与 billing 已发布事实数不等（有事实被拒入死信）时失败关闭，即使最大 factId 相同。 */
    @Test
    void test_appliedCountMismatchFails() {
        postRevenue("10");
        billingFacts.mockProgress(OE, ledger.getEntityNo(), ledger.getCurrency(), 0, 2, 9002L);
        accountPostings.mockProgress(OE, ledger.getEntityNo(), ledger.getCurrency(), 1, 9002L);

        assertFailedAt(STEP_3, "1");
    }

    /** 步骤 3 段 ②：ledger 对某条过账流的已消费序号落后于 account 最大序号时失败关闭；追平后再次关账通过。 */
    @Test
    void test_ledgerBehindThenCaughtUp() {
        postRevenue("10");
        billingFacts.mockProgress(OE, ledger.getEntityNo(), ledger.getCurrency(), 0, 2, 9002L);
        accountPostings.mockProgress(OE, ledger.getEntityNo(), ledger.getCurrency(), 2, 9002L,
            new AccountPostingMocker.StreamProgress(OE, ledger.getEntityNo(), ledger.getCurrency(), 0, 5));
        String stream = AccountPostedPayload.aggregateId(AccountEntityType.OPERATING_ENTITY, ledger.getEntityNo(),
            ledger.getCurrency(), 0);
        testMapper.insertConsumedVersion(AccountPostedPayload.AGGREGATE_TYPE, stream, 4);

        assertFailedAt(STEP_3, "1");

        testMapper.updateConsumedVersion(AccountPostedPayload.AGGREGATE_TYPE, stream, 5);
        startClose().assertSuccess(201).assertText("status", "PENDING_CHECK").assertText("closeNo", "2");
        assertEquals(List.of(progressCall(), progressCall()), accountPostings.calls());
    }

    /** 步骤 3：billing 或 account 不可用时失败关闭，不按“无事实”放行。 */
    @Test
    void test_dependencyUnavailableFailsClosed() {
        postRevenue("10");
        billingFacts.mockProgressFailure(OE, ledger.getEntityNo(), ledger.getCurrency(), BillingFactMocker.unavailable());

        assertFailedAt(STEP_3, "1");

        billingFacts.mockProgress(OE, ledger.getEntityNo(), ledger.getCurrency(), 0, 0, null);
        accountPostings.mockProgressFailure(OE, ledger.getEntityNo(), ledger.getCurrency(),
            AccountPostingMocker.unavailable());

        assertFailedAt(STEP_3, "2");
    }

    /** account 进度调用记录：本账簿主体。 */
    String progressCall() {
        return "progress:" + OE + "/" + ledger.getEntityNo() + "/" + ledger.getCurrency();
    }

    @Test
    void test_earlierPeriodMustBeClosed() {
        long earlier = ledgerMocker.mockPeriod(ledger.getFiscalCalendarId(), 2020, 1, LocalDate.of(2020, 1, 1),
            LocalDate.of(2020, 1, 31), PeriodStatus.OPEN);

        startClose().assertRejected(LedgerErrorCode.PERIOD_CLOSE_ORDER_INVALID);
        assertEquals(0, testMapper.countPeriodCloses(ledger.getOpenPeriodId()));

        testMapper.updatePeriodStatus(earlier, PeriodStatus.CLOSED.getCode());
        startClose().assertSuccess(201).assertText("status", "PENDING_CHECK");
    }

    @Test
    void test_invalidPeriodRejected() {
        startClose(123456789L).assertBusinessRejected("会计期间不存在: 123456789");

        long future = ledgerMocker.mockPeriod(ledger.getFiscalCalendarId(), 2099, 1, LocalDate.of(2099, 1, 1),
            LocalDate.of(2099, 1, 31), PeriodStatus.FUTURE);
        startClose(future).assertRejected(LedgerErrorCode.PERIOD_NOT_OPEN);
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

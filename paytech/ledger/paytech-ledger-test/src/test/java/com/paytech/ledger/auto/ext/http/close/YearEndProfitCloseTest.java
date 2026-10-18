package com.paytech.ledger.auto.ext.http.close;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.core.dal.dataobject.JournalLine;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.PeriodCloseStatus;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.test.base.AbstractPeriodReopenApiTest;
import com.paytech.ledger.test.builder.SubmitManualAdjustmentRequestBuilder;
import com.paytech.ledger.test.checker.LedgerResponseChecker;

/**
 * 年末结转：财年最后期间关账时本期损益全年累计（含此前月份）转入留存收益，盈利与亏损方向、本期无收入费用仍结转，
 * 非年末期间不结转，重开年末期间一并冲回。
 *
 * @see com.paytech.ledger.api.PeriodCloseApi#startPeriodClose(String, com.paytech.ledger.api.request.StartPeriodCloseRequestBody)
 * @see com.paytech.ledger.api.PeriodCloseApi#approvePeriodClose(String, String, com.paytech.ledger.api.request.ReviewPeriodCloseRequestBody)
 */
class YearEndProfitCloseTest extends AbstractPeriodReopenApiTest {

    @Test
    void test_yearEndProfitClosedToRetainedEarnings() {
        makeCurrentPeriodYearEnd();
        postRevenue("300");
        postExpense("120");

        LedgerResponseChecker close = startClose().assertSuccess(201)
            .assertText("status", PeriodCloseStatus.PENDING_CHECK.getCode());

        long resultAccountId = currentResultAccountId();
        Long retainedId = retainedEarningsAccountId();
        assertNotNull(retainedId, "年末结转应自动创建账簿主体的留存收益账户");
        assertNetBalance(revenueAccountId, "0");
        assertNetBalance(expenseAccountId, "0");
        assertNetBalance(resultAccountId, "0");
        assertNetBalance(retainedId, "180");
        List<JournalLine> lines = postedLines(ledger.getOpenPeriodId(), close.text("closeNo"));
        assertEquals(5, lines.size(), "收入、费用、本期损益净额 3 行 + 年末两行");
        assertYearEndLines(lines, resultAccountId, retainedId, EntrySide.DEBIT, "180");
    }

    @Test
    void test_yearEndIncludesPriorMonthResult() {
        makeCurrentPeriodYearEnd();
        LocalDate priorStart = LocalDate.now().withDayOfMonth(1).minusMonths(1);
        long priorPeriodId = ledgerMocker.mockPeriod(ledger.getFiscalCalendarId(), priorStart.getYear(),
            priorStart.getMonthValue(), priorStart, priorStart.withDayOfMonth(priorStart.lengthOfMonth()),
            PeriodStatus.OPEN);
        posted(SubmitManualAdjustmentRequestBuilder.template(ledger.getLedgerBookId(), priorPeriodId)
            .transfer(capitalAccountId, revenueAccountId, "100"));
        LedgerResponseChecker priorClose = closeAndApprove(priorPeriodId);
        assertNull(retainedEarningsAccountId(), "前一月份不是财年最后期间，不结转留存收益");
        assertEquals(2, postedLines(priorPeriodId, priorClose.text("closeNo")).size());
        assertNetBalance(currentResultAccountId(), "100");
        postRevenue("300");
        postExpense("120");

        LedgerResponseChecker close = startClose().assertSuccess(201)
            .assertText("status", PeriodCloseStatus.PENDING_CHECK.getCode());

        long resultAccountId = currentResultAccountId();
        long retainedId = retainedEarningsAccountId();
        assertNetBalance(resultAccountId, "0");
        assertNetBalance(retainedId, "280");
        assertYearEndLines(postedLines(ledger.getOpenPeriodId(), close.text("closeNo")), resultAccountId, retainedId,
            EntrySide.DEBIT, "280");
    }

    @Test
    void test_yearEndLossDebitsRetainedEarnings() {
        makeCurrentPeriodYearEnd();
        postRevenue("50");
        postExpense("200");

        LedgerResponseChecker close = startClose().assertSuccess(201)
            .assertText("status", PeriodCloseStatus.PENDING_CHECK.getCode());

        long resultAccountId = currentResultAccountId();
        long retainedId = retainedEarningsAccountId();
        assertNetBalance(resultAccountId, "0");
        assertNetBalance(retainedId, "-150");
        assertYearEndLines(postedLines(ledger.getOpenPeriodId(), close.text("closeNo")), resultAccountId, retainedId,
            EntrySide.CREDIT, "150");
    }

    @Test
    void test_yearEndWithoutIncomeStillClosesAccumulatedResult() {
        makeCurrentPeriodYearEnd();
        long resultAccountId = ledgerMocker.mockAccount(ledger.getLedgerBookId(), CURRENT_RESULT_CODE,
            OwnerType.OPERATING_ENTITY, ledger.getEntityNo(), BalanceBucket.NONE);
        posted(adjustment().transfer(capitalAccountId, resultAccountId, "70"));

        LedgerResponseChecker close = startClose().assertSuccess(201)
            .assertText("status", PeriodCloseStatus.PENDING_CHECK.getCode());

        long retainedId = retainedEarningsAccountId();
        assertNetBalance(resultAccountId, "0");
        assertNetBalance(retainedId, "70");
        List<JournalLine> lines = postedLines(ledger.getOpenPeriodId(), close.text("closeNo"));
        assertEquals(2, lines.size(), "本期无收入费用时只有年末两行");
        assertYearEndLines(lines, resultAccountId, retainedId, EntrySide.DEBIT, "70");
    }

    @Test
    void test_nonYearEndHasNoRetainedEarningsLine() {
        postRevenue("300");

        LedgerResponseChecker close = startClose().assertSuccess(201)
            .assertText("status", PeriodCloseStatus.PENDING_CHECK.getCode());

        assertNull(retainedEarningsAccountId(), "非年末期间不创建留存收益账户");
        assertNetBalance(currentResultAccountId(), "300");
        List<JournalLine> lines = postedLines(ledger.getOpenPeriodId(), close.text("closeNo"));
        assertEquals(2, lines.size());
        long resultAccountId = currentResultAccountId();
        assertTrue(lines.stream().allMatch(line -> line.getAccountId() == revenueAccountId
            || line.getAccountId() == resultAccountId), "非年末只有收入→本期损益行");
    }

    @Test
    void test_reopenYearEndReversesRetainedEarnings() {
        makeCurrentPeriodYearEnd();
        postRevenue("300");
        postExpense("120");
        closeAndApprove();
        long retainedId = retainedEarningsAccountId();
        assertNetBalance(retainedId, "180");

        approveReopen(startedReopen()).assertSuccess(200)
            .assertText("status", PeriodCloseStatus.COMPLETED.getCode());

        assertEquals(PeriodStatus.OPEN.getCode(), testMapper.selectPeriodStatus(ledger.getOpenPeriodId()));
        assertNetBalance(retainedId, "0");
        assertNetBalance(currentResultAccountId(), "0");
        assertNetBalance(revenueAccountId, "300");
        assertNetBalance(expenseAccountId, "120");
    }

    /** 结转分录的年末两行：本期损益与留存收益同额，本期损益在给定方向、留存收益相反。 */
    void assertYearEndLines(List<JournalLine> lines, long resultAccountId, long retainedId, EntrySide resultSide,
        String amount) {
        EntrySide retainedSide = resultSide == EntrySide.DEBIT ? EntrySide.CREDIT : EntrySide.DEBIT;
        List<JournalLine> retained = lines.stream().filter(line -> line.getAccountId() == retainedId).toList();
        assertEquals(1, retained.size(), "留存收益行数");
        assertEquals(retainedSide, retained.getFirst().getEntrySide());
        assertEquals(0, new BigDecimal(amount).compareTo(retained.getFirst().getAmount()));
        boolean resultLine = lines.stream().anyMatch(line -> line.getAccountId() == resultAccountId
            && line.getEntrySide() == resultSide && new BigDecimal(amount).compareTo(line.getAmount()) == 0);
        assertTrue(resultLine, "缺少本期损益 " + resultSide.getCode() + " " + amount + " 的年末行");
    }
}

package com.paytech.finance.auto.ext.http.accounting.trial;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.finance.accounting.core.constant.StandardAccountCodes;
import com.paytech.finance.accounting.core.enums.AccountClass;
import com.paytech.finance.accounting.core.enums.BalanceBucket;
import com.paytech.finance.accounting.core.enums.LedgerEntityType;
import com.paytech.finance.accounting.core.enums.NormalSide;
import com.paytech.finance.accounting.core.enums.OwnerType;
import com.paytech.finance.accounting.core.enums.PeriodStatus;
import com.paytech.finance.accounting.core.enums.TrialBalanceStatus;
import com.paytech.finance.accounting.core.enums.TrialExceptionStatus;
import com.paytech.finance.accounting.core.enums.TrialExceptionType;
import com.paytech.finance.accounting.core.enums.TrialReferenceType;
import com.paytech.finance.test.base.AbstractTrialBalanceApiTest;
import com.paytech.finance.test.mocker.local.LedgerMocker;

import tools.jackson.databind.JsonNode;

/**
 * 执行试算：期初/本期/期末汇总、借贷合计、水位与期间过滤、投影漂移异常、期间余额投影与拒绝场景。
 *
 * @see com.paytech.finance.api.accounting.TrialBalanceApi#runTrialBalance(String, String)
 */
class RunTrialBalanceTest extends AbstractTrialBalanceApiTest {
    /** 初始化 DML 中 BANK_CASH 科目的固定主键。 */
    private static final long BANK_CASH_CHART_ACCOUNT_ID = 3003L;

    @Test
    void test_linesOpeningPeriodClosingAndBalancedTotals() {
        long historyPeriod = mockHistoryPeriod();
        long futurePeriod = ledgerMocker.mockPeriod(ledger.getFiscalCalendarId(), 2099, 1, LocalDate.of(2099, 1, 1),
            LocalDate.of(2099, 1, 31), PeriodStatus.OPEN);
        ledgerMocker.mockAccount(ledger.getLedgerBookId(), StandardAccountCodes.CLIENT_SECURITY_DEPOSIT,
            OwnerType.CLIENT, "1001", BalanceBucket.NONE);
        postedIn(historyPeriod, bankCashAccountId, capitalAccountId, "100");
        postedIn(ledger.getOpenPeriodId(), bankCashAccountId, capitalAccountId, "50");
        postedIn(ledger.getOpenPeriodId(), clientAvailableAccountId, bankCashAccountId, "30");
        postedIn(futurePeriod, bankCashAccountId, capitalAccountId, "7");

        var run = runTrial(ledger.getOpenPeriodId()).assertSuccess(201);

        assertAll(() -> assertEquals(TrialBalanceStatus.SUCCEEDED.getCode(), run.text("status")),
            () -> assertEquals("1", run.text("runNo")), () -> assertEquals("4", run.text("asOfSequence")),
            () -> assertEquals("150.00", run.text("totalDebit")), () -> assertEquals("150.00", run.text("totalCredit")),
            () -> assertEquals("3", run.text("lineCount")), () -> assertEquals("0", run.text("exceptionCount")),
            () -> assertTrue(run.text("contentHash").matches("[0-9a-f]{64}")),
            () -> assertTrue(run.text("createTime") != null));
        List<JsonNode> lines = detailArray(run.text("runNo"), "lines");
        assertEquals(List.of("BANK_CASH", "CAPITAL", StandardAccountCodes.CLIENT_PAYABLE),
            lines.stream().map(line -> line.path("accountCode").asString()).toList(), "无发生额的保证金科目不落行");
        assertLine(lines.get(0), AccountClass.ASSET, NormalSide.DEBIT, "100.00", "50.00", "30.00", "120.00");
        assertLine(lines.get(1), AccountClass.EQUITY, NormalSide.CREDIT, "100.00", "0.00", "50.00", "150.00");
        assertLine(lines.get(2), AccountClass.LIABILITY, NormalSide.CREDIT, "0.00", "30.00", "0.00", "-30.00");
    }

    @Test
    void test_projectionDriftFailsWithoutPeriodBalance() {
        postedIn(ledger.getOpenPeriodId(), bankCashAccountId, capitalAccountId, "100");
        testMapper.updateAccountNetBalance(bankCashAccountId, "130");

        var run = runTrial(ledger.getOpenPeriodId()).assertSuccess(201)
            .assertText("status", TrialBalanceStatus.FAILED.getCode()).assertText("exceptionCount", "1");

        JsonNode exception = detailArray(run.text("runNo"), "exceptions").getFirst();
        assertAll(() -> assertEquals(TrialExceptionType.GENERAL_LEDGER_MISMATCH.getCode(),
                exception.path("exceptionType").asString()),
            () -> assertEquals(TrialReferenceType.ACCOUNT.getCode(), exception.path("referenceType").asString()),
            () -> assertEquals(String.valueOf(bankCashAccountId), exception.path("referenceId").asString()),
            () -> assertEquals("30.00", exception.path("differenceAmount").asString()),
            () -> assertEquals(TrialExceptionStatus.OPEN.getCode(), exception.path("status").asString()));
        assertTrue(testMapper.selectPeriodBalances(ledger.getOpenPeriodId(), ledger.getLedgerBookId()).isEmpty(),
            "失败试算不得刷新期间余额");
    }

    @Test
    void test_periodBalanceAdvancesOnlyToNewerWatermark() {
        postedIn(ledger.getOpenPeriodId(), bankCashAccountId, capitalAccountId, "10");
        runTrial(ledger.getOpenPeriodId()).assertSuccess(201);
        assertPeriodBalance(1L, "10.00");

        postedIn(ledger.getOpenPeriodId(), bankCashAccountId, capitalAccountId, "5");
        runTrial(ledger.getOpenPeriodId()).assertSuccess(201).assertText("runNo", "2").assertText("asOfSequence", "2");
        assertPeriodBalance(2L, "15.00");

        testMapper.updatePeriodBalanceSequence(ledger.getOpenPeriodId(), ledger.getLedgerBookId(), 99L);
        postedIn(ledger.getOpenPeriodId(), bankCashAccountId, capitalAccountId, "1");
        runTrial(ledger.getOpenPeriodId()).assertSuccess(201)
            .assertText("status", TrialBalanceStatus.SUCCEEDED.getCode()).assertText("asOfSequence", "3");
        assertPeriodBalance(99L, "15.00");
    }

    @Test
    void test_bookOrPeriodRejected() {
        long otherCalendar = ledgerMocker.mockCalendar(LedgerEntityType.OPERATING_ENTITY, "AE002");
        long foreignPeriod = ledgerMocker.mockPeriod(otherCalendar, 2099, 1, LocalDate.of(2099, 1, 1),
            LocalDate.of(2099, 1, 31), PeriodStatus.OPEN);
        long futurePeriod = ledgerMocker.mockPeriod(ledger.getFiscalCalendarId(), 2099, 2, LocalDate.of(2099, 2, 1),
            LocalDate.of(2099, 2, 28), PeriodStatus.FUTURE);
        long otherBook = ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE003", "USD",
            LedgerMocker.STANDARD_CHART_ID);

        post(trialPath(123456789L, ledger.getOpenPeriodId()), null).assertBusinessRejected("账簿不存在: 123456789");
        runTrial(123456789L).assertBusinessRejected("会计期间不存在: 123456789");
        runTrial(foreignPeriod).assertBusinessRejected(
            "会计期间不属于账簿主体: 期间 " + foreignPeriod + ", 账簿 " + ledger.getLedgerBookId());
        post(trialPath(otherBook, ledger.getOpenPeriodId()), null).assertBusinessRejected("会计期间不属于账簿主体");
        runTrial(futurePeriod).assertBusinessRejected("会计期间未开放，不能试算: " + futurePeriod);
        assertEquals(0, testMapper.countTrialRuns(ledger.getLedgerBookId()));
    }

    @Test
    void test_closedPeriodCanBeTrialed() {
        postedIn(ledger.getOpenPeriodId(), bankCashAccountId, capitalAccountId, "10");
        testMapper.updatePeriodStatus(ledger.getOpenPeriodId(), PeriodStatus.CLOSED.getCode());

        runTrial(ledger.getOpenPeriodId()).assertSuccess(201)
            .assertText("status", TrialBalanceStatus.SUCCEEDED.getCode());
    }

    /** 读取试算详情中的数组字段。 */
    List<JsonNode> detailArray(String runNo, String field) {
        var detail = get(trialPath(ledger.getLedgerBookId(), ledger.getOpenPeriodId()) + "/" + runNo).assertSuccess(200);
        List<JsonNode> items = new ArrayList<>();
        detail.as(JsonNode.class).path(field).forEach(items::add);
        return items;
    }

    /** 断言一行试算的科目属性与四个金额。 */
    void assertLine(JsonNode line, AccountClass accountClass, NormalSide normalSide, String opening, String debit,
        String credit, String closing) {
        assertAll(line.path("accountCode").asString(),
            () -> assertEquals(accountClass.getCode(), line.path("accountClass").asString()),
            () -> assertEquals(normalSide.getCode(), line.path("normalSide").asString()),
            () -> assertEquals(opening, line.path("openingBalance").asString()),
            () -> assertEquals(debit, line.path("periodDebit").asString()),
            () -> assertEquals(credit, line.path("periodCredit").asString()),
            () -> assertEquals(closing, line.path("closingBalance").asString()));
    }

    /** 断言期间余额只有银行现金与实收资本两行，且水位与银行现金期末一致。 */
    void assertPeriodBalance(long asOfSequence, String bankClosing) {
        List<Map<String, Object>> rows = testMapper.selectPeriodBalances(ledger.getOpenPeriodId(),
            ledger.getLedgerBookId());
        assertEquals(2, rows.size());
        rows.forEach(row -> assertEquals(asOfSequence, ((Number)row.get("as_of_sequence")).longValue()));
        Map<String, Object> bank = rows.stream()
            .filter(row -> ((Number)row.get("chart_account_id")).longValue() == BANK_CASH_CHART_ACCOUNT_ID).findFirst()
            .orElseThrow();
        assertEquals(0, new BigDecimal(bankClosing).compareTo((BigDecimal)bank.get("closing_balance")));
    }
}

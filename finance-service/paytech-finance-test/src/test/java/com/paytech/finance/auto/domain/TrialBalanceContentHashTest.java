package com.paytech.finance.auto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceException;
import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceLine;
import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceRun;
import com.paytech.finance.accounting.core.enums.TrialBalanceStatus;
import com.paytech.finance.accounting.domain.TrialBalanceContentHash;

/**
 * 试算内容摘要的固定向量
 * <li>期望值由独立的 RFC 8785 实现（键排序、无空白）离线计算，不调用被测算法生成。</li>
 *
 * @see TrialBalanceContentHash#compute(TrialBalanceRun, List, List, int)
 */
class TrialBalanceContentHashTest {
    /** 两行、无异常的离线 SHA-256。 */
    private static final String EXPECTED = "4bc294e97b098c59895f76aa3215ba16998f195bf05ad92464543b8eda4f408e";

    @Test
    void test_fixedVectorIndependentOfLineOrderAndScale() {
        List<TrialBalanceLine> lines = List.of(line(3019L, "0", "0", "100", "100"), line(3003L, "0", "100.0", "0", "100"));

        assertEquals(EXPECTED, TrialBalanceContentHash.compute(run(), lines, List.<TrialBalanceException>of(), 2));
    }

    @Test
    void test_anyContentChangeChangesHash() {
        TrialBalanceRun run = run();
        run.setAsOfSequence(6L);
        List<TrialBalanceLine> lines = List.of(line(3003L, "0", "100", "0", "100"), line(3019L, "0", "0", "100", "100"));

        assertNotEquals(EXPECTED, TrialBalanceContentHash.compute(run, lines, List.<TrialBalanceException>of(), 2));
    }

    /** 固定向量的试算头。 */
    TrialBalanceRun run() {
        TrialBalanceRun run = new TrialBalanceRun();
        run.setLedgerBookId(11L);
        run.setPeriodId(22L);
        run.setRunNo(1);
        run.setAsOfSequence(5L);
        run.setStatus(TrialBalanceStatus.SUCCEEDED);
        run.setTotalDebit(new BigDecimal("100"));
        run.setTotalCredit(new BigDecimal("100.000"));
        return run;
    }

    /** 固定向量的试算行。 */
    TrialBalanceLine line(long chartAccountId, String opening, String debit, String credit, String closing) {
        TrialBalanceLine line = new TrialBalanceLine();
        line.setChartAccountId(chartAccountId);
        line.setOpeningBalance(new BigDecimal(opening));
        line.setPeriodDebit(new BigDecimal(debit));
        line.setPeriodCredit(new BigDecimal(credit));
        line.setClosingBalance(new BigDecimal(closing));
        return line;
    }
}

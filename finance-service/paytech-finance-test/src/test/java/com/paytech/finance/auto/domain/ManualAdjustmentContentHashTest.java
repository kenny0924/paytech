package com.paytech.finance.auto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.finance.accounting.core.dal.dataobject.ManualAdjustment;
import com.paytech.finance.accounting.core.dal.dataobject.ManualAdjustmentLine;
import com.paytech.finance.accounting.core.enums.EntrySide;
import com.paytech.finance.accounting.domain.ManualAdjustmentContentHash;

/**
 * 人工调整内容摘要的固定向量
 * <li>期望值由独立的 RFC 8785 实现（键排序、无空白、UTF-8 原文）离线计算，不调用被测算法生成。</li>
 *
 * @see ManualAdjustmentContentHash#compute(ManualAdjustment, List, int)
 */
class ManualAdjustmentContentHashTest {
    /** 第 1 行“借方 \"说明\"”、第 2 行“贷方”的离线 SHA-256。 */
    private static final String EXPECTED = "7a72d1484aa46623252004e1a8882be2db49cccf71dc03dd548b306c5590bf9b";

    @Test
    void test_fixedVectorIndependentOfLineOrderAndAmountScale() {
        ManualAdjustment adjustment = adjustment();
        List<ManualAdjustmentLine> lines = List.of(line(2, 34L, EntrySide.CREDIT, "100", "贷方"),
            line(1, 33L, EntrySide.DEBIT, "100.0", "借方 \"说明\""));

        assertEquals(EXPECTED, ManualAdjustmentContentHash.compute(adjustment, lines, 2));
    }

    @Test
    void test_anyContentChangeChangesHash() {
        ManualAdjustment adjustment = adjustment();
        adjustment.setReasonCode("OTHER");
        List<ManualAdjustmentLine> lines = List.of(line(1, 33L, EntrySide.DEBIT, "100", "借方 \"说明\""),
            line(2, 34L, EntrySide.CREDIT, "100", "贷方"));

        assertNotEquals(EXPECTED, ManualAdjustmentContentHash.compute(adjustment, lines, 2));
    }

    /** 固定向量的调整头。 */
    ManualAdjustment adjustment() {
        ManualAdjustment adjustment = new ManualAdjustment();
        adjustment.setAdjustmentNo("MA1001");
        adjustment.setLedgerBookId(11L);
        adjustment.setAccountingPeriodId(22L);
        adjustment.setCurrency("USD");
        adjustment.setReasonCode("CORRECTION");
        adjustment.setMakerId(70001L);
        adjustment.setTotalDebit(new BigDecimal("100"));
        adjustment.setTotalCredit(new BigDecimal("100.000"));
        return adjustment;
    }

    /** 固定向量的明细。 */
    ManualAdjustmentLine line(int lineNo, long accountId, EntrySide side, String amount, String description) {
        ManualAdjustmentLine line = new ManualAdjustmentLine();
        line.setLineNo(lineNo);
        line.setAccountId(accountId);
        line.setEntrySide(side);
        line.setAmount(new BigDecimal(amount));
        line.setLineDescription(description);
        return line;
    }
}

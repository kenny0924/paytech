package com.paytech.ledger.auto.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.domain.ChartBalanceRollup;
import com.paytech.ledger.domain.view.AccountBalanceView;

/**
 * 级次汇总的纯计算：多级累加、备抵科目（与上级方向相反）按上级方向换算、末级描述补齐与数据不一致失败。
 *
 * @see ChartBalanceRollup#summarize(List)
 */
class ChartBalanceRollupTest {
    /** 一级应收（借） → 二级应收明细（借） → 三级：客户应收（借）与坏账准备（贷，备抵）。 */
    private final ChartAccount receivables = account(1L, null, "1101", NormalSide.DEBIT, false);

    private final ChartAccount trade = account(2L, 1L, "110101", NormalSide.DEBIT, false);

    private final ChartAccount customer = account(3L, 2L, "11010101", NormalSide.DEBIT, true);

    private final ChartAccount allowance = account(4L, 2L, "11010102", NormalSide.CREDIT, true);

    @Test
    void test_multiLevelRollupWithContraAccount() {
        AccountBalanceView customerLine = leaf(3L, NormalSide.DEBIT, "100.00", "50.00", "20.00", "130.00");
        AccountBalanceView allowanceLine = leaf(4L, NormalSide.CREDIT, "10.00", "0.00", "5.00", "15.00");

        List<AccountBalanceView> summaries = ChartBalanceRollup.of(List.of(receivables, trade, customer, allowance))
            .summarize(List.of(customerLine, allowanceLine));

        assertEquals(List.of("1101", "110101"), summaries.stream().map(AccountBalanceView::getAccountNo).toList());
        // 借方科目汇总：期初 100 - 10、期末 130 - 15；发生额直接相加
        for (AccountBalanceView summary : summaries) {
            assertAll(summary.getAccountNo(), () -> assertAmount("90.00", summary.getOpeningBalance()),
                () -> assertAmount("50.00", summary.getPeriodDebit()), () -> assertAmount("25.00", summary.getPeriodCredit()),
                () -> assertAmount("115.00", summary.getClosingBalance()),
                () -> assertEquals(Boolean.FALSE, summary.getPostingAllowed()));
        }
        assertAll(() -> assertEquals(1, summaries.get(0).getLevel()), () -> assertEquals(2, summaries.get(1).getLevel()),
            () -> assertEquals(1L, summaries.get(1).getParentChartAccountId()),
            () -> assertEquals(3, customerLine.getLevel()), () -> assertEquals("11010102", allowanceLine.getAccountNo()),
            () -> assertEquals(2L, allowanceLine.getParentChartAccountId()));
    }

    @Test
    void test_creditSummaryExpressesDebitChildNegative() {
        ChartAccount payables = account(5L, null, "2201", NormalSide.CREDIT, false);
        ChartAccount payable = account(6L, 5L, "220101", NormalSide.CREDIT, true);
        ChartAccount prepaid = account(7L, 5L, "220102", NormalSide.DEBIT, true);

        List<AccountBalanceView> summaries = ChartBalanceRollup.of(List.of(payables, payable, prepaid)).summarize(List.of(
            leaf(6L, NormalSide.CREDIT, "0.00", "0.00", "80.00", "80.00"),
            leaf(7L, NormalSide.DEBIT, "0.00", "30.00", "0.00", "30.00")));

        AccountBalanceView summary = summaries.getFirst();
        assertAll(() -> assertAmount("50.00", summary.getClosingBalance()), () -> assertAmount("30.00", summary.getPeriodDebit()),
            () -> assertAmount("80.00", summary.getPeriodCredit()));
    }

    @Test
    void test_leafOutsideChartFails() {
        assertThrows(ErrorException.class, () -> ChartBalanceRollup.of(List.of(receivables))
            .summarize(List.of(leaf(99L, NormalSide.DEBIT, "0.00", "1.00", "0.00", "1.00"))));
    }

    /** 构造科目；代码由编号派生。 */
    static ChartAccount account(long id, Long parentId, String accountNo, NormalSide side, boolean postingAllowed) {
        ChartAccount account = new ChartAccount();
        account.setId(id);
        account.setParentAccountId(parentId);
        account.setAccountNo(accountNo);
        account.setAccountCode("ACCOUNT_" + accountNo);
        account.setAccountName("科目 " + accountNo);
        account.setAccountClass(accountNo.startsWith("1") ? AccountClass.ASSET : AccountClass.LIABILITY);
        account.setNormalSide(side);
        account.setPostingAllowed(postingAllowed);
        return account;
    }

    /** 构造末级行金额。 */
    static AccountBalanceView leaf(long chartAccountId, NormalSide side, String opening, String debit, String credit,
        String closing) {
        AccountBalanceView line = new AccountBalanceView();
        line.setChartAccountId(chartAccountId);
        line.setNormalSide(side);
        line.setOpeningBalance(new BigDecimal(opening));
        line.setPeriodDebit(new BigDecimal(debit));
        line.setPeriodCredit(new BigDecimal(credit));
        line.setClosingBalance(new BigDecimal(closing));
        return line;
    }

    /** 金额按数值相等比较。 */
    static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "期望 " + expected + "，实际 " + actual);
    }
}

package com.paytech.ledger.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.domain.view.ProfitCloseBalanceView;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 损益结转分录行计算（纯计算）
 * <li>每个收入/费用账户按本期借贷净额反向冲为零：借方余额记贷、贷方余额记借。</li>
 * <li>按结转规则把各来源账户的借减贷净额汇总到目标本期损益账户：合计为正记借（亏损），为负记贷（利润）。</li>
 * <li>净额为零的账户与目标不生成行；结果为空表示本期无需结转。</li>
 * <li>财年最后期间另按本期损益账户累计结余生成转入留存收益的两行。</li>
 */
public final class ProfitClosePlan {

    private ProfitClosePlan() {
    }

    /**
     * 计算结转行
     *
     * @param balances 收入/费用账户本期借贷发生额
     * @param resultAccountBySourceChart 来源科目主键 → 目标本期损益账户主键
     * @return 先来源账户、后目标账户的结转行
     */
    public static List<Line> lines(List<ProfitCloseBalanceView> balances, Map<Long, Long> resultAccountBySourceChart) {
        List<Line> lines = new ArrayList<>();
        Map<Long, BigDecimal> resultNet = new TreeMap<>();
        for (ProfitCloseBalanceView balance : balances) {
            BigDecimal net = balance.getPeriodDebit().subtract(balance.getPeriodCredit());
            if (net.signum() == 0) {
                continue;
            }
            Long resultAccountId = resultAccountBySourceChart.get(balance.getChartAccountId());
            if (resultAccountId == null) {
                throw new ErrorException("收入/费用科目缺少结转目标账户: " + balance.getChartAccountId());
            }
            lines.add(new Line(balance.getAccountId(), net.signum() > 0 ? EntrySide.CREDIT : EntrySide.DEBIT, net.abs()));
            resultNet.merge(resultAccountId, net, BigDecimal::add);
        }
        resultNet.forEach((accountId, net) -> {
            if (net.signum() != 0) {
                lines.add(new Line(accountId, net.signum() > 0 ? EntrySide.DEBIT : EntrySide.CREDIT, net.abs()));
            }
        });
        return lines;
    }

    /**
     * 本次结转行对某账户的贷减借净影响
     *
     * @param planned 已计算的结转行
     * @param accountId 账户主键
     * @return 贷方为正、借方为负的净额
     */
    public static BigDecimal creditNet(List<Line> planned, long accountId) {
        return planned.stream().filter(line -> line.getAccountId() == accountId)
            .map(line -> line.getEntrySide() == EntrySide.CREDIT ? line.getAmount() : line.getAmount().negate())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 年末把本期损益账户结余转入留存收益
     * <li>贷方结余（盈利）：借本期损益、贷留存收益；借方结余（亏损）相反；金额取绝对值。</li>
     * <li>结余为零返回空。</li>
     *
     * @param resultAccountId 本期损益账户主键
     * @param retainedAccountId 留存收益账户主键
     * @param creditBalance 本期损益账户贷减借结余（含本次结转行）
     * @return 先本期损益、后留存收益的两行，或空
     */
    public static List<Line> retainedEarningsLines(long resultAccountId, long retainedAccountId, BigDecimal creditBalance) {
        if (creditBalance.signum() == 0) {
            return List.of();
        }
        EntrySide resultSide = creditBalance.signum() > 0 ? EntrySide.DEBIT : EntrySide.CREDIT;
        EntrySide retainedSide = resultSide == EntrySide.DEBIT ? EntrySide.CREDIT : EntrySide.DEBIT;
        BigDecimal amount = creditBalance.abs();
        return List.of(new Line(resultAccountId, resultSide, amount), new Line(retainedAccountId, retainedSide, amount));
    }

    /** 一条结转行。 */
    @Getter
    @AllArgsConstructor
    public static class Line {
        /** 记账账户主键。 */
        private final long accountId;

        /** 借贷方向。 */
        private final EntrySide entrySide;

        /** 大于零的金额。 */
        private final BigDecimal amount;
    }
}

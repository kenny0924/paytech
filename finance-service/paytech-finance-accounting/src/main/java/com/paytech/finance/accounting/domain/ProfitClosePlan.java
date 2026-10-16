package com.paytech.finance.accounting.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.paytech.finance.accounting.core.enums.EntrySide;
import com.paytech.finance.accounting.domain.view.ProfitCloseBalanceView;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 损益结转分录行计算（纯计算）
 * <li>每个收入/费用账户按本期借贷净额反向冲为零：借方余额记贷、贷方余额记借。</li>
 * <li>按结转规则把各来源账户的借减贷净额汇总到目标本期损益账户：合计为正记借（亏损），为负记贷（利润）。</li>
 * <li>净额为零的账户与目标不生成行；结果为空表示本期无需结转。</li>
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

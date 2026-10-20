package com.paytech.ledger.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.domain.view.AccountBalanceView;
import com.paytech.ledger.domainservice.validator.chart.ChartAccountNoRule;

/**
 * 科目余额的级次汇总（纯计算，L6 科目余额与 M4 试算详情共用）
 * <li>输入科目表全部科目与末级行；末级行按其科目补齐编号、上级与级次。</li>
 * <li>沿 parent_account_id 自下而上累加：本期借、贷直接相加；期初、期末先按各自正常方向换算为借方为正的金额再相加，最后按汇总科目自身
 * 的正常方向表达，因此任一汇总科目的借、贷与余额等于其直接下级之和，一级科目合计借贷平衡。</li>
 * <li>只产生有下级金额的汇总行；不改变试算平衡的持久化行与内容摘要。</li>
 */
public final class ChartBalanceRollup {
    private final Map<Long, ChartAccount> accounts;

    private ChartBalanceRollup(Map<Long, ChartAccount> accounts) {
        this.accounts = accounts;
    }

    /** 以科目表全部科目构造汇总器。 */
    public static ChartBalanceRollup of(List<ChartAccount> chartAccounts) {
        return new ChartBalanceRollup(chartAccounts.stream()
            .collect(Collectors.toMap(ChartAccount::getId, Function.identity())));
    }

    /**
     * 补齐末级行的科目描述并计算全部汇总行
     *
     * @param leaves 末级行，金额已按币种精度规范；本方法补齐其编号、上级、级次等描述字段
     * @return 汇总行，按科目编号升序
     */
    public List<AccountBalanceView> summarize(List<AccountBalanceView> leaves) {
        Map<Long, AccountBalanceView> summaries = new LinkedHashMap<>();
        for (AccountBalanceView leaf : leaves) {
            describe(leaf, account(leaf.getChartAccountId()));
            Long parentId = leaf.getParentChartAccountId();
            for (int depth = 0; parentId != null; depth++) {
                if (depth >= ChartAccountNoRule.MAX_LEVEL) {
                    throw new ErrorException("科目树超过最大级次或成环: 科目 " + leaf.getChartAccountId());
                }
                AccountBalanceView summary = summaries.computeIfAbsent(parentId, this::emptySummary);
                add(summary, leaf);
                parentId = summary.getParentChartAccountId();
            }
        }
        List<AccountBalanceView> result = new ArrayList<>(summaries.values());
        result.sort(Comparator.comparing(AccountBalanceView::getAccountNo));
        return result;
    }

    /** 读取科目；行引用的科目不在科目表中说明数据不一致。 */
    ChartAccount account(Long chartAccountId) {
        ChartAccount account = accounts.get(chartAccountId);
        if (account == null) {
            throw new ErrorException("余额行引用的科目不在账簿科目表中: " + chartAccountId);
        }
        return account;
    }

    /** 从科目补齐行的描述字段。 */
    static void describe(AccountBalanceView row, ChartAccount account) {
        row.setChartAccountId(account.getId());
        row.setAccountNo(account.getAccountNo());
        row.setAccountCode(account.getAccountCode());
        row.setAccountName(account.getAccountName());
        row.setAccountClass(account.getAccountClass());
        row.setNormalSide(account.getNormalSide());
        row.setParentChartAccountId(account.getParentAccountId());
        row.setLevel(ChartAccountNoRule.level(account.getAccountNo()));
        row.setPostingAllowed(account.getPostingAllowed());
    }

    /** 金额为零的汇总行。 */
    AccountBalanceView emptySummary(Long chartAccountId) {
        AccountBalanceView summary = new AccountBalanceView();
        describe(summary, account(chartAccountId));
        summary.setOpeningBalance(BigDecimal.ZERO);
        summary.setPeriodDebit(BigDecimal.ZERO);
        summary.setPeriodCredit(BigDecimal.ZERO);
        summary.setClosingBalance(BigDecimal.ZERO);
        return summary;
    }

    /** 把末级行金额并入汇总行：余额经借方为正的金额换算到汇总科目自身的正常方向。 */
    static void add(AccountBalanceView summary, AccountBalanceView leaf) {
        summary.setOpeningBalance(summary.getOpeningBalance()
            .add(convert(leaf.getOpeningBalance(), leaf.getNormalSide(), summary.getNormalSide())));
        summary.setPeriodDebit(summary.getPeriodDebit().add(leaf.getPeriodDebit()));
        summary.setPeriodCredit(summary.getPeriodCredit().add(leaf.getPeriodCredit()));
        summary.setClosingBalance(summary.getClosingBalance()
            .add(convert(leaf.getClosingBalance(), leaf.getNormalSide(), summary.getNormalSide())));
    }

    /** 把按 from 方向有符号的余额换算为按 to 方向有符号的余额。 */
    static BigDecimal convert(BigDecimal amount, NormalSide from, NormalSide to) {
        return from == to ? amount : amount.negate();
    }
}

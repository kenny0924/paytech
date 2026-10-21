package com.paytech.reconciliation.domain;

import java.util.List;

import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 渠道账单对平门禁（设计 F1 四项 + 决定 I-04「到账银行账户已配置」），全部满足才能对平
 * <li>交易行：已匹配到平台流水，或已经 CLASSIFY 归类（行上有处置 ID）。</li>
 * <li>非交易行（ADJUSTMENT、FEE）：已 MATCHED（带交易号的 FEE 行随交易行并入，其余经 CLASSIFY 归类）。</li>
 * <li>纯计算：只依据调用方已加载的账单、清算账户、全部行与本账单未结异常。</li>
 */
@Getter
@AllArgsConstructor
public class ReconcileGate {
    /** 每条交易行都已匹配到平台流水或已归类 */
    private final boolean transactionLinesMatched;

    /** 每条 ADJUSTMENT 行与 FEE 行都已并入交易行或已有 APPLIED 的归类处置 */
    private final boolean adjustmentLinesClassified;

    /** 本账单没有阻塞性的未结异常 */
    private final boolean noBlockingException;

    /** 各行按方向回放后与账单头总额、费用、净额一致 */
    private final boolean totalsConsistent;

    /** 净额方向为 NONE，或清算账户已配置到账银行账户 */
    private final boolean settlementCashAccountConfigured;

    /**
     * 计算门禁
     *
     * @param statement 账单头
     * @param clearingAccount 所属清算账户
     * @param lines 全部行
     * @param activeExceptions 本账单未结异常
     * @return 门禁五项
     */
    public static ReconcileGate evaluate(ChannelStatement statement, ClearingAccount clearingAccount,
        List<ChannelStatementLine> lines, List<ReconException> activeExceptions) {
        boolean transactionsMatched = lines.stream().filter(line -> ChannelStatementLines.isTransactionLine(line.getLineType()))
            .allMatch(ReconcileGate::transactionLineResolved);
        boolean adjustmentsClassified = lines.stream()
            .filter(line -> !ChannelStatementLines.isTransactionLine(line.getLineType()))
            .allMatch(ReconcileGate::nonTransactionLineResolved);
        boolean noBlocking = activeExceptions.stream().noneMatch(exception -> Boolean.TRUE.equals(exception.getBlocking()));
        boolean totals = ChannelStatementTotals.mismatches(statement, lines).isEmpty();
        boolean cashAccount = statement.getCashDirection() == CashDirection.NONE
            || clearingAccount.getSettlementCashAccountId() != null;
        return new ReconcileGate(transactionsMatched, adjustmentsClassified, noBlocking, totals, cashAccount);
    }

    /** 五项是否全部满足。 */
    public boolean passed() {
        return transactionLinesMatched && adjustmentLinesClassified && noBlockingException && totalsConsistent
            && settlementCashAccountConfigured;
    }

    /** 交易行：MATCHED 且指向平台流水或已归类。 */
    static boolean transactionLineResolved(ChannelStatementLine line) {
        return line.getMatchStatus() == LineMatchStatus.MATCHED
            && (line.getPlatformTxnId() != null || line.getResolutionId() != null);
    }

    /** 非交易行：ADJUSTMENT 与不带交易号的 FEE 行须已归类；带交易号的 FEE 行 MATCHED 即可（并入交易行或已归类）。 */
    static boolean nonTransactionLineResolved(ChannelStatementLine line) {
        if (line.getMatchStatus() != LineMatchStatus.MATCHED) {
            return false;
        }
        boolean folded = line.getLineType() == ChannelLineType.FEE && ChannelStatementLines.hasReference(line);
        return folded || line.getResolutionId() != null;
    }
}

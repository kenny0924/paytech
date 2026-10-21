package com.paytech.reconciliation.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;

/**
 * 账单头与明细的恒等式（technique §2.5）：完成导入时校验，对平门禁在锁内复核
 * <li>grossAmount = Σ CREDIT 行 amount − Σ DEBIT 行 amount（不含 FEE 行）。</li>
 * <li>feeAmount = Σ 明细 feeAmount + Σ FEE 行 amount。</li>
 * <li>netAmount 带 cashDirection 等于 grossAmount − feeAmount；cashDirection 为 NONE 时 netAmount 必须为 0。</li>
 */
public final class ChannelStatementTotals {
    private ChannelStatementTotals() {
    }

    /**
     * 核对恒等式，返回不一致的原因（为空表示一致）
     *
     * @param statement 账单头
     * @param lines 全部明细行
     * @return 可外显的中文原因，按总额、费用、净额顺序
     */
    public static List<String> mismatches(ChannelStatement statement, List<ChannelStatementLine> lines) {
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        for (ChannelStatementLine line : lines) {
            fee = fee.add(line.getFeeAmount());
            if (line.getLineType() == ChannelLineType.FEE) {
                fee = fee.add(line.getAmount());
                continue;
            }
            gross = gross.add(ChannelStatementLines.signedAmount(line));
        }
        List<String> reasons = new ArrayList<>();
        if (gross.compareTo(statement.getGrossAmount()) != 0) {
            reasons.add("明细总额 " + gross.toPlainString() + " 与账单头总额 " + statement.getGrossAmount().toPlainString() + " 不一致");
        }
        if (fee.compareTo(statement.getFeeAmount()) != 0) {
            reasons.add("明细费用 " + fee.toPlainString() + " 与账单头费用 " + statement.getFeeAmount().toPlainString() + " 不一致");
        }
        BigDecimal signedNet = signedNet(statement);
        if (statement.getCashDirection() == CashDirection.NONE && statement.getNetAmount().signum() != 0) {
            reasons.add("净额方向为 NONE 时净额必须为 0");
        } else if (gross.subtract(fee).compareTo(signedNet) != 0) {
            reasons.add("总额减费用 " + gross.subtract(fee).toPlainString() + " 与账单头带方向净额 " + signedNet.toPlainString() + " 不一致");
        }
        return reasons;
    }

    /** 账单头带方向净额：CREDIT 为正，DEBIT 为负，NONE 为 0。 */
    public static BigDecimal signedNet(ChannelStatement statement) {
        if (statement.getCashDirection() == CashDirection.NONE) {
            return BigDecimal.ZERO;
        }
        return statement.getCashDirection() == CashDirection.CREDIT ? statement.getNetAmount()
            : statement.getNetAmount().negate();
    }
}

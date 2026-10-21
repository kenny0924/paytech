package com.paytech.reconciliation.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;

/**
 * 渠道账单行的纯业务规则（标准账单 CSV，technique §2.5；设计 F1 规则 2、7）
 * <li>交易行：PAYMENT（CREDIT，对应应收）、REFUND / CHARGEBACK（DEBIT，对应应付）；必须带外部交易号。</li>
 * <li>净额贡献：非 FEE 行为「带方向金额 − 行上费用」，FEE 行为「−(金额 + 行上费用)」；全部行的贡献之和等于账单头带方向净额。
 * 行级异常的差异金额与归类金额都取该贡献，使「应收 − 应付 − 实际费用 + Σ 归类金额 = 净额」成立。</li>
 */
public final class ChannelStatementLines {
    private ChannelStatementLines() {
    }

    /** 是否交易行（PAYMENT、REFUND、CHARGEBACK）：须匹配到平台流水。 */
    public static boolean isTransactionLine(ChannelLineType lineType) {
        return lineType == ChannelLineType.PAYMENT || lineType == ChannelLineType.REFUND
            || lineType == ChannelLineType.CHARGEBACK;
    }

    /** 交易行要求的资金方向：PAYMENT 为 CREDIT，REFUND / CHARGEBACK 为 DEBIT；非交易行为空。 */
    public static SettlementDirection requiredDirection(ChannelLineType lineType) {
        if (lineType == ChannelLineType.PAYMENT) {
            return SettlementDirection.CREDIT;
        }
        return isTransactionLine(lineType) ? SettlementDirection.DEBIT : null;
    }

    /** 交易行对应的平台流水方向（设计 F1 规则 2）；非交易行为空。 */
    public static ClearingDirection platformDirection(ChannelLineType lineType) {
        if (lineType == ChannelLineType.PAYMENT) {
            return ClearingDirection.RECEIVABLE;
        }
        return isTransactionLine(lineType) ? ClearingDirection.PAYABLE : null;
    }

    /** 行是否带外部交易号（类型与交易号同有同无，完成导入时校验）。 */
    public static boolean hasReference(ChannelStatementLine line) {
        return line.getReferenceType() != null && line.getExternalTransactionId() != null;
    }

    /** 两行是否同一外部交易号。 */
    public static boolean sameReference(ChannelStatementLine left, ChannelStatementLine right) {
        return hasReference(left) && Objects.equals(left.getReferenceType(), right.getReferenceType())
            && Objects.equals(left.getExternalTransactionId(), right.getExternalTransactionId());
    }

    /** 按方向带符号的行金额：CREDIT 为正，DEBIT 为负。 */
    public static BigDecimal signedAmount(ChannelStatementLine line) {
        return line.getDirection() == SettlementDirection.CREDIT ? line.getAmount() : line.getAmount().negate();
    }

    /** 本行对账单带方向净额的贡献（见类注释）。 */
    public static BigDecimal netContribution(ChannelStatementLine line) {
        if (line.getLineType() == ChannelLineType.FEE) {
            return line.getAmount().add(line.getFeeAmount()).negate();
        }
        return signedAmount(line).subtract(line.getFeeAmount());
    }

    /**
     * 交易行的实际渠道费：行上费用加同一账单内同交易号 FEE 行金额（设计 F1 规则 7，technique D-05）
     *
     * @param transactionLine 交易行
     * @param statementLines 同一账单的全部行
     * @return 实际渠道费
     */
    public static BigDecimal actualFee(ChannelStatementLine transactionLine, List<ChannelStatementLine> statementLines) {
        BigDecimal fee = transactionLine.getFeeAmount();
        for (ChannelStatementLine line : statementLines) {
            if (line.getLineType() == ChannelLineType.FEE && sameReference(transactionLine, line)) {
                fee = fee.add(line.getAmount()).add(line.getFeeAmount());
            }
        }
        return fee;
    }
}

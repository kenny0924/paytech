package com.paytech.reconciliation.domain;

import java.math.BigDecimal;
import java.util.List;

import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 作废未对平渠道账单的上下文（UC-R08）
 * <li>释放本账单已处理的行（匹配、实际费用、归类全部清空回 UNMATCHED），回退被其覆盖的平台流水进度。</li>
 */
@Getter
@AllArgsConstructor
public class VoidChannelStatementContext {
    /** 待作废的账单（RECEIVING 或 MATCHING） */
    private final ChannelStatement statement;

    /** 需要释放的行（状态不是 UNMATCHED） */
    private final List<ChannelStatementLine> releasedLines;

    /** 需要回退进度的平台流水 */
    private final List<TxnRollback> txnRollbacks;

    /** 一笔平台流水回退后的进度。 */
    @Getter
    @AllArgsConstructor
    public static class TxnRollback {
        private final PlatformTxn txn;

        private final BigDecimal matchedAmount;

        private final BigDecimal actualFee;

        private final PlatformTxnStatus status;
    }
}

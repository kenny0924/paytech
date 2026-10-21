package com.paytech.reconciliation.domain;

import java.util.Set;

import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ReconLayer;

/**
 * 异常类型派生的对账层与阻塞属性（设计 §7.9，DDL t_recon_exception.blocking 注释）
 * <li>银行层：BANK_LINE_UNMATCHED、BANK_AMOUNT_MISMATCH、BANK_MOVEMENT_OVERDUE、BANK_STATEMENT_GAP、LATE_DEBIT，全部不阻塞。</li>
 * <li>交易层：PLATFORM_ITEM_UNSETTLED 不阻塞，其余阻塞所属渠道账单对平。</li>
 */
public final class ReconExceptionTypes {
    private static final Set<ReconExceptionType> BANK_TYPES = Set.of(ReconExceptionType.BANK_LINE_UNMATCHED,
        ReconExceptionType.BANK_AMOUNT_MISMATCH, ReconExceptionType.BANK_MOVEMENT_OVERDUE,
        ReconExceptionType.BANK_STATEMENT_GAP, ReconExceptionType.LATE_DEBIT);

    private ReconExceptionTypes() {
    }

    /** 异常类型所属对账层。 */
    public static ReconLayer layerOf(ReconExceptionType type) {
        return BANK_TYPES.contains(type) ? ReconLayer.BANK : ReconLayer.TRANSACTION;
    }

    /** 异常类型是否阻塞所属渠道账单对平。 */
    public static boolean isBlocking(ReconExceptionType type) {
        return layerOf(type) == ReconLayer.TRANSACTION && type != ReconExceptionType.PLATFORM_ITEM_UNSETTLED;
    }
}

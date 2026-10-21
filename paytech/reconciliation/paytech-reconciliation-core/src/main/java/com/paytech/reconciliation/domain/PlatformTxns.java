package com.paytech.reconciliation.domain;

import java.math.BigDecimal;

import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;

/** 平台流水进度的纯规则（设计 §6.4 不变量 1：settled ≤ matched ≤ expected）。 */
public final class PlatformTxns {
    private PlatformTxns() {
    }

    /**
     * 由已匹配金额推出的进度状态（不含 SETTLED）
     *
     * @param txn 平台流水
     * @param matchedAmount 新的已匹配金额
     * @return 0 为 OPEN，未满为 PARTIALLY_MATCHED，满额为 MATCHED
     */
    public static PlatformTxnStatus progressStatus(PlatformTxn txn, BigDecimal matchedAmount) {
        if (matchedAmount.signum() == 0) {
            return PlatformTxnStatus.OPEN;
        }
        return matchedAmount.compareTo(txn.getExpectedAmount()) < 0 ? PlatformTxnStatus.PARTIALLY_MATCHED
            : PlatformTxnStatus.MATCHED;
    }

    /** 是否仍可被账单行匹配（OPEN、PARTIALLY_MATCHED）。 */
    public static boolean matchable(PlatformTxn txn) {
        return txn.getStatus() == PlatformTxnStatus.OPEN || txn.getStatus() == PlatformTxnStatus.PARTIALLY_MATCHED;
    }

    /** 实际渠道费与预估之差（实际 − 预估）。 */
    public static BigDecimal feeDifference(PlatformTxn txn) {
        return txn.getActualFee().subtract(txn.getExpectedFee());
    }
}

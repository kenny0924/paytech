package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 平台流水来源 billing 事实类型（t_platform_txn.txn_source）
 * <li>切片 1 只产生 PAYMENT、REFUND；其余值先登记、不产生。</li>
 */
public enum PlatformTxnSource implements CodeEnum {
    /** 支付成功，应收 */
    PAYMENT,

    /** 重复扣款，应收（切片 2） */
    DUPLICATE_CAPTURE,

    /** 退款成功，应付 */
    REFUND,

    /** 重复扣款补偿，应付（切片 2） */
    DUPLICATE_COMPENSATION,

    /** 拒付扣款，应付（切片 2） */
    CHARGEBACK,

    /** 拒付撤销，应收（切片 2） */
    CHARGEBACK_REVERSAL;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}

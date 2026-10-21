package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 平台流水来源业务对象类型（t_platform_txn.source_ref_type）
 * <li>切片 1 只产生 PAYMENT_ATTEMPT、CHANNEL_REFUND。</li>
 */
public enum PlatformTxnSourceRefType implements CodeEnum {
    /** 支付尝试；Owner payment */
    PAYMENT_ATTEMPT,

    /** 渠道退款；Owner payment */
    CHANNEL_REFUND,

    /** 重复扣款明细；Owner payment */
    DUPLICATE_ITEM,

    /** 渠道争议通知；Owner payment */
    DISPUTE_NOTICE;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}

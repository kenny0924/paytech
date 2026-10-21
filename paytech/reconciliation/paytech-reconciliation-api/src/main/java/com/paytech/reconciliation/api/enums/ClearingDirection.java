package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 平台流水方向（t_platform_txn.direction）
 */
public enum ClearingDirection implements CodeEnum {
    /** 渠道欠平台：支付类事实产生的待清算应收 */
    RECEIVABLE,

    /** 平台欠渠道：退款、拒付类事实产生的待清算应付 */
    PAYABLE;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}

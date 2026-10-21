package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 资金方向，相对平台（渠道账单行、银行流水、预期银行流水的 direction）
 */
public enum SettlementDirection implements CodeEnum {
    /** 流入平台 */
    CREDIT,

    /** 流出平台 */
    DEBIT;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}

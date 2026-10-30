package com.paytech.payout.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 收款方式（t_beneficiary_account.rail_type），决定字段要求
 */
public enum BeneficiaryRailType implements CodeEnum {
    /** 本地清算 */
    LOCAL,

    /** SWIFT 跨境 */
    SWIFT;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}

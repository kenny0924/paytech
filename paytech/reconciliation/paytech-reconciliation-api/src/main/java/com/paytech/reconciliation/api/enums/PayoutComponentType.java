package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 出款银行流水的组成部分（t_bank_match.payout_component_type）
 * <li>只用于 PAYOUT（切片 3）。</li>
 */
public enum PayoutComponentType implements CodeEnum {
    /** 付给收款人的金额 */
    BENEFICIARY_TRANSFER,

    /** 银行手续费 */
    BANK_FEE,

    /** 收款人金额与手续费合并扣款 */
    COMBINED;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}

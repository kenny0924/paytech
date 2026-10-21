package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 重新匹配目标类型（t_recon_resolution.rematch_target_type）
 */
public enum RematchTargetType implements CodeEnum {
    /** 交易层：账单行改挂的平台流水 */
    PLATFORM_TXN,

    /** 银行层：银行流水追加匹配的预期银行流水 */
    EXPECTED_BANK_MOVEMENT;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}

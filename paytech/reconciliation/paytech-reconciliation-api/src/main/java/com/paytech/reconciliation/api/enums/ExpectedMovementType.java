package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 预期银行流水来源（t_expected_bank_movement.movement_type）
 * <li>决定确认后发哪个事件；切片 1 只产生 CHANNEL_SETTLEMENT。</li>
 */
public enum ExpectedMovementType implements CodeEnum {
    /** 渠道账单净额，source_id 为渠道账单 ID */
    CHANNEL_SETTLEMENT,

    /** 出款执行（切片 3） */
    PAYOUT,

    /** 缴税（切片 3） */
    TAX_PAYMENT;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}

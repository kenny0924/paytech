package com.paytech.billing.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 渠道成本组成的金额计算基数。 */
public enum ChannelCostCalculationBase implements CodeEnum {
    /** 估算调用方传入的交易金额 */
    TRANSACTION_AMOUNT;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}

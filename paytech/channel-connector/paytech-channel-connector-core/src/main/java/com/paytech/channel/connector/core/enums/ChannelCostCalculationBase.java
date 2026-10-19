package com.paytech.channel.connector.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道成本组成的金额计算基数。 */
@Getter
@AllArgsConstructor
public enum ChannelCostCalculationBase implements CodeEnum {
    /** 计算调用方传入的交易金额 */
    TRANSACTION_AMOUNT("TRANSACTION_AMOUNT");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}

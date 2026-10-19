package com.paytech.channel.connector.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 把渠道成本原始金额归整到币种精度的固定方式；归整由 billing 计算时执行。 */
@Getter
@AllArgsConstructor
public enum AmountRoundingMode implements CodeEnum {
    /** 四舍五入 */
    HALF_UP("HALF_UP");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}

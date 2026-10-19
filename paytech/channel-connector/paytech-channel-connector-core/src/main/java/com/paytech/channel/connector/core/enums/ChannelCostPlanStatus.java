package com.paytech.channel.connector.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道成本方案稳定身份的生命周期状态。 */
@Getter
@AllArgsConstructor
public enum ChannelCostPlanStatus implements CodeEnum {
    /** 已创建、尚无当前版本 */
    DRAFT("DRAFT"),

    /** 已有当前版本 */
    ACTIVE("ACTIVE"),

    /** 已停用，本期不提供停用入口 */
    INACTIVE("INACTIVE");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}

package com.paytech.channel.connector.protocol.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 人工变更留痕的被变更对象类型；targetId 为该对象在本库的主键。 */
@Getter
@AllArgsConstructor
public enum ChangeTargetType implements CodeEnum {
    /** 支付渠道（t_payment_channel） */
    CHANNEL("CHANNEL"),

    /** 渠道技术账号（t_channel_account） */
    CHANNEL_ACCOUNT("CHANNEL_ACCOUNT"),

    /** 渠道 MID（t_channel_mid） */
    CHANNEL_MID("CHANNEL_MID"),

    /** MID 能力条件组（t_mid_capability） */
    MID_CAPABILITY("MID_CAPABILITY"),

    /** 渠道成本方案版本（t_channel_cost_plan_ver） */
    CHANNEL_COST_VERSION("CHANNEL_COST_VERSION"),

    /** 标准 API（t_standard_api） */
    STANDARD_API("STANDARD_API"),

    /** 接入方（t_integration） */
    INTEGRATION("INTEGRATION"),

    /** 渠道原子 API（t_integration_api） */
    INTEGRATION_API("INTEGRATION_API"),

    /** 接入方公共脚本（t_integration_script） */
    INTEGRATION_SCRIPT("INTEGRATION_SCRIPT"),

    /** 标准 API 实现（t_integration_standard_api） */
    INTEGRATION_IMPLEMENTATION("INTEGRATION_IMPLEMENTATION"),

    /** 沙箱验证账号（t_integration_verify_account） */
    VERIFY_ACCOUNT("VERIFY_ACCOUNT"),

    /** 沙箱验证用例（t_integration_verify_case） */
    VERIFY_CASE("VERIFY_CASE");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}

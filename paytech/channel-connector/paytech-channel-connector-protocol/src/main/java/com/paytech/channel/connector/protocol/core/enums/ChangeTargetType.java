package com.paytech.channel.connector.protocol.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 人工变更留痕的被变更对象类型；targetId 为该对象在本库的主键。 */
@Getter
@AllArgsConstructor
public enum ChangeTargetType implements CodeEnum {
    /** 渠道（t_channel）：创建、名称与说明修改、状态变更与出站地址替换（CONFIGURATION） */
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

    /**
     * 原外部接入方（已并入 t_channel）
     * <li>只为读取第二段合表前、已删除接入方的历史记录保留，不再写入；能对应到渠道的历史记录已迁移为 CHANNEL。</li>
     */
    INTEGRATION("INTEGRATION"),

    /** 渠道原子 API（t_channel_api） */
    CHANNEL_API("CHANNEL_API"),

    /** 渠道公共脚本（t_channel_script） */
    CHANNEL_SCRIPT("CHANNEL_SCRIPT"),

    /** 渠道标准 API 实现（t_channel_standard_api） */
    CHANNEL_IMPLEMENTATION("CHANNEL_IMPLEMENTATION"),

    /** 沙箱验证账号（t_channel_verify_account） */
    VERIFY_ACCOUNT("VERIFY_ACCOUNT"),

    /** 沙箱验证用例（t_channel_verify_case） */
    VERIFY_CASE("VERIFY_CASE"),

    /** 服务渠道调用授权（t_channel_call_grant） */
    CHANNEL_CALL_GRANT("CHANNEL_CALL_GRANT");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}

package com.paytech.channel.connector.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道成本方案版本的审批与生效状态。 */
@Getter
@AllArgsConstructor
public enum ChannelCostVersionStatus implements CodeEnum {
    /** 草稿，适用范围与成本组成已随版本一次写入 */
    DRAFT("DRAFT"),

    /** 已提交待复核 */
    PENDING_APPROVAL("PENDING_APPROVAL"),

    /** 已批准但尚未到生效时间，到期由切换 Job 设为当前版本 */
    APPROVED("APPROVED"),

    /** 当前版本 */
    ACTIVE("ACTIVE"),

    /** 复核驳回 */
    REJECTED("REJECTED"),

    /** 被新版本替代，保留历史区间供按发生时间命中 */
    INACTIVE("INACTIVE");

    /** 数据库存储及业务比较使用的稳定代码。 */
    private final String code;
}

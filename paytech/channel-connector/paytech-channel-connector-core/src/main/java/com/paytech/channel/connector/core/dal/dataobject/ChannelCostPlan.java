package com.paytech.channel.connector.core.dal.dataobject;

import java.time.Instant;

import com.paytech.channel.connector.core.enums.ChannelCostPlanStatus;

import lombok.Getter;
import lombok.Setter;

/** t_channel_cost_plan：渠道成本方案稳定身份与当前版本指针；每个 Channel 一个方案。 */
@Getter
@Setter
public class ChannelCostPlan {
    /**
     * 渠道成本方案的 Snowflake 主键
     */
    private Long id;

    /**
     * Payment Channel（本库 t_payment_channel.id）
     */
    private Long channelId;

    /**
     * 跨版本稳定识别方案的业务代码
     */
    private String planCode;

    /**
     * 当前版本主键
     * <li>可为空；DRAFT 方案尚无当前版本。</li>
     */
    private Long currentVerId;

    /**
     * 生命周期状态
     */
    private ChannelCostPlanStatus status;

    /**
     * 切换当前版本与状态时使用的乐观锁版本号
     */
    private Long version;

    /**
     * 该记录在本库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}

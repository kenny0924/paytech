package com.paytech.channel.connector.api.change.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 一条渠道配置或成本版本的人工变更记录。 */
@Getter
@Setter
public class ChannelConfigChangeResponseBody {
    /** 变更记录主键，十进制字符串。 */
    private String changeId;

    /**
     * 被变更对象类型
     * <li>CHANNEL、CHANNEL_ACCOUNT、CHANNEL_MID、MID_CAPABILITY、CHANNEL_COST_VERSION。</li>
     */
    private String targetType;

    /** 被变更对象在本库的主键，十进制字符串。 */
    private String targetId;

    /**
     * 变更类型
     * <li>CREATE、UPDATE、STATUS、CONFIGURATION、REBIND、CANCEL。</li>
     */
    private String changeType;

    /**
     * 状态变更前的状态
     * <li>非状态变更为空。</li>
     */
    private String fromStatus;

    /**
     * 状态变更后的状态
     * <li>非状态变更为空。</li>
     */
    private String toStatus;

    /** 执行变更的 Platform Actor 稳定业务 ID，十进制字符串。 */
    private String operatorId;

    /**
     * 操作者填写的原因
     * <li>未填写时为空。</li>
     */
    private String reason;

    /** 变更记录创建时间（UTC）。 */
    private Instant createTime;
}

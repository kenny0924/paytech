package com.paytech.channel.connector.domain;

import java.time.Instant;

import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;

import lombok.Getter;
import lombok.Setter;

/** MID 列表项：MID、Channel 编码、当前账号名称与能力组计数的同库组合查询结果。 */
@Getter
@Setter
public class ChannelMidSummaryView {
    /** MID 主键。 */
    private Long midId;

    /** 所属 Channel 主键。 */
    private Long channelId;

    /**
     * 所属 Channel 编码
     * <li>Channel 行缺失时为空。</li>
     */
    private String channelCode;

    /** 当前绑定的技术账号主键。 */
    private Long channelAccountId;

    /**
     * 当前绑定的技术账号名称
     * <li>账号行缺失时为空。</li>
     */
    private String accountName;

    /** 渠道分配的外部商户号。 */
    private String externalMidCode;

    /** 所属 Operating Entity 编号。 */
    private String entityNo;

    /** MID 状态。 */
    private ChannelStatus midStatus;

    /** 能力条件组总数。 */
    private Integer capabilityGroupCount;

    /** ENABLED 能力条件组数。 */
    private Integer enabledCapabilityGroupCount;

    /**
     * 首次激活时间
     * <li>从未激活时为空。</li>
     */
    private Instant activatedAt;

    /** 最后更新时间。 */
    private Instant updateTime;
}

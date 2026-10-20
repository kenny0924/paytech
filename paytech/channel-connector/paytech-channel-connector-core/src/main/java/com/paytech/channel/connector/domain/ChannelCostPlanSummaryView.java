package com.paytech.channel.connector.domain;

import java.time.Instant;

import com.paytech.channel.connector.core.enums.ChannelCostPlanStatus;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本方案列表项：方案、Channel 编码与名称、当前版本及待复核版本数的同库组合查询结果。 */
@Getter
@Setter
public class ChannelCostPlanSummaryView {
    /** 方案主键。 */
    private Long planId;

    /** 方案代码。 */
    private String planCode;

    /** Payment Channel 主键。 */
    private Long channelId;

    /**
     * Channel 编码
     * <li>Channel 行缺失时为空。</li>
     */
    private String channelCode;

    /**
     * Channel 名称
     * <li>Channel 行缺失时为空。</li>
     */
    private String channelName;

    /** 方案状态。 */
    private ChannelCostPlanStatus status;

    /**
     * 当前版本号
     * <li>尚无当前版本时为空。</li>
     */
    private Integer currentCostVer;

    /**
     * 当前版本生效时间
     * <li>尚无当前版本时为空。</li>
     */
    private Instant currentEffectiveFrom;

    /** PENDING_APPROVAL 版本数。 */
    private Integer pendingApprovalCount;

    /** 方案最后更新时间。 */
    private Instant updateTime;
}

package com.paytech.channel.connector.api.cost.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 渠道成本方案列表项。 */
@Getter
@Setter
public class ChannelCostPlanSummaryResponseBody {
    /** 方案主键，十进制字符串。 */
    private String planId;

    /** 方案代码。 */
    private String planCode;

    /** Payment Channel 主键，十进制字符串。 */
    private String channelId;

    /** Channel 编码。 */
    private String channelCode;

    /** Channel 名称。 */
    private String channelName;

    /** 方案状态：DRAFT、ACTIVE、INACTIVE。 */
    private String status;

    /**
     * 当前版本号
     * <li>尚无当前版本时为空。</li>
     */
    private String currentCostVer;

    /**
     * 当前版本开始参与匹配的时间（UTC）
     * <li>尚无当前版本时为空。</li>
     */
    private Instant currentEffectiveFrom;

    /** 待复核（PENDING_APPROVAL）版本数。 */
    private Integer pendingApprovalCount;

    /** 方案最后更新时间（UTC）。 */
    private Instant updateTime;
}

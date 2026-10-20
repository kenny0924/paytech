package com.paytech.channel.connector.api.channel.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** MID 列表项。 */
@Getter
@Setter
public class ChannelMidSummaryResponseBody {
    /** MID 主键，十进制字符串。 */
    private String midId;

    /** 所属 Channel 主键，十进制字符串。 */
    private String channelId;

    /** 所属 Channel 编码。 */
    private String channelCode;

    /** 当前绑定的技术账号主键，十进制字符串。 */
    private String channelAccountId;

    /** 当前绑定的技术账号展示名称。 */
    private String accountName;

    /** 渠道分配的外部商户号。 */
    private String externalMidCode;

    /** 所属 Operating Entity 编号。 */
    private String entityNo;

    /**
     * MID 状态
     * <li>DRAFT、ACTIVE、SUSPENDED、CLOSED。</li>
     */
    private String status;

    /** 能力条件组总数。 */
    private Integer capabilityGroupCount;

    /** 启用（ENABLED）的能力条件组数。 */
    private Integer enabledCapabilityGroupCount;

    /**
     * 首次激活时间（UTC）
     * <li>从未激活时为空。</li>
     */
    private Instant activatedAt;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}

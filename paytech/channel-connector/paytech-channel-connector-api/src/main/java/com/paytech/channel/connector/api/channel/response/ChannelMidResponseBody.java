package com.paytech.channel.connector.api.channel.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** MID 详情，含全部能力条件组与明细。 */
@Getter
@Setter
public class ChannelMidResponseBody {
    /** MID ID，十进制字符串 */
    private String midId;

    /** 所属渠道 ID，十进制字符串 */
    private String channelId;

    /** 当前绑定的技术账号 ID，十进制字符串 */
    private String channelAccountId;

    /** 渠道分配的商户号 */
    private String externalMidCode;

    /** 所属 Operating Entity 编号 */
    private String entityNo;

    /** MID 状态：DRAFT、ACTIVE、SUSPENDED、CLOSED */
    private String status;

    /** 首次激活时间，UTC；未激活过为 null */
    private Instant activatedAt;

    /** 关闭时间，UTC；未关闭为 null */
    private Instant closedAt;

    /** 创建时间，UTC */
    private Instant createTime;

    /** 最后修改时间，UTC */
    private Instant updateTime;

    /** 能力条件组，按 ID 升序 */
    private List<MidCapabilityResponseBody> capabilities;
}

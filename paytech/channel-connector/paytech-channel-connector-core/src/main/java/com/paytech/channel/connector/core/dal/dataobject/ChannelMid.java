package com.paytech.payment.channel.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;
import com.paytech.payment.channel.core.enums.ChannelStatus;


/** ChannelMid 对应的 Channel 所属持久化对象。 */
@Getter
@Setter
public class ChannelMid {
    /** MID 内部标识。 */
    private Long midId;

    /** 所属渠道标识。 */
    private Long channelId;

    /** 当前绑定的技术账号，不覆盖历史冻结值。 */
    private Long channelAccountId;

    /** 渠道分配的商户号，不等同于 outletId。 */
    private String externalMidCode;

    /** 所属 OE 编号，须与当前账号一致。 */
    private String entityNo;

    /** MID 生命周期状态。 */
    private ChannelStatus midStatus;

    /** 首次激活 UTC 时间，可空。 */
    private Instant activatedAt;

    /** 关闭 UTC 时间，可空。 */
    private Instant closedAt;

    /** 创建 UTC 时间，微秒精度。 */
    private Instant createTime;

    /** 最后变更 UTC 时间，微秒精度。 */
    private Instant updateTime;
}

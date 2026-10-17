package com.paytech.channel.connector.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;
import com.paytech.channel.connector.core.enums.ChannelStatus;


/** PaymentChannel 对应的 Channel 所属持久化对象。 */
@Getter
@Setter
public class PaymentChannel {
    /** 渠道内部标识。 */
    private Long channelId;

    /** 不可变渠道代码。 */
    private String channelCode;

    /** 渠道名称。 */
    private String channelName;

    /** 渠道生命周期状态。 */
    private ChannelStatus channelStatus;

    /** 创建 UTC 时间，微秒精度。 */
    private Instant createTime;

    /** 最后变更 UTC 时间，微秒精度。 */
    private Instant updateTime;
}

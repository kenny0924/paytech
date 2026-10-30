package com.paytech.commercial.domain;

import lombok.Value;
import lombok.experimental.Accessors;

/** channel-connector 对一组支付方式 × 币种 × MCC 的就绪结论。 */
@Value
@Accessors(fluent = true)
public class ChannelIdentityReadiness {
    /** 核对的组合。 */
    ChannelIdentityItem item;

    /** 至少一个可用 MID 能承接收款。 */
    boolean ready;

    /** 不就绪原因码（channel-connector ChannelIdentityReadinessReason）；就绪时为空。 */
    String reasonCode;
}

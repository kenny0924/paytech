package com.paytech.channel.connector.domain;

import com.paytech.channel.connector.api.enums.ChannelIdentityReadinessReason;
import com.paytech.channel.connector.service.processor.request.ChannelIdentityReadinessItem;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一组支付方式 × 币种 × MCC 的渠道身份就绪结论。 */
@Getter
@AllArgsConstructor
public class ChannelIdentityReadiness {
    /** 判定的组合。 */
    private final ChannelIdentityReadinessItem item;

    /** 不就绪原因；就绪时为空。 */
    private final ChannelIdentityReadinessReason reason;

    /** 至少一个可用 MID 能承接该组合的收款。 */
    public boolean ready() {
        return reason == null;
    }
}

package com.paytech.channel.connector.api.readiness.response;

import lombok.Getter;
import lombok.Setter;

/** 一组支付方式 × 币种 × MCC 的就绪结论。 */
@Getter
@Setter
public class ChannelIdentityReadinessResultBody {
    /** 回显的支付方式目录代码。 */
    private String paymentMethodCode;

    /** 回显的交易币种。 */
    private String currency;

    /** 回显的 MCC。 */
    private String mccCode;

    /** 至少一个可用 MID 能承接该组合的收款。 */
    private boolean ready;

    /**
     * 不就绪原因（取值见 ChannelIdentityReadinessReason）
     * <li>ready=true 时为空；不就绪时给出"最接近满足"的一个原因。</li>
     */
    private String reasonCode;
}

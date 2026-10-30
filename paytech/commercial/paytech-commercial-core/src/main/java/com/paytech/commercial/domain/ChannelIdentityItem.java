package com.paytech.commercial.domain;

import lombok.Value;
import lombok.experimental.Accessors;

/** ④ 渠道身份待核对的一组支付方式、币种与 Client 主 MCC（某 OE 下）。 */
@Value
@Accessors(fluent = true)
public class ChannelIdentityItem {
    /** 开通项目录的支付方式代码。 */
    String paymentMethodCode;

    /** 价格已覆盖的币种。 */
    String currency;

    /** Client 已确认的主 MCC。 */
    String mccCode;
}

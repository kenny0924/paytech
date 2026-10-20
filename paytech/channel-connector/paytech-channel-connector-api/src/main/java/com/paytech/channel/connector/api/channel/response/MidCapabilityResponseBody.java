package com.paytech.channel.connector.api.channel.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** MID 能力条件组及其明细。 */
@Getter
@Setter
public class MidCapabilityResponseBody {
    /** 能力条件组 ID，十进制字符串 */
    private String midCapabilityId;

    /** 所属 MID ID，十进制字符串 */
    private String midId;

    /** 精确支付方式目录代码 */
    private String paymentMethodCode;

    /** 支付体系代码或 * */
    private String schemeCode;

    /** 国家代码或 * */
    private String countryCode;

    /** 币种或 * */
    private String currency;

    /** 四位 MCC */
    private String mccCode;

    /** 整组状态：ENABLED、DISABLED */
    private String status;

    /** 创建时间，UTC */
    private Instant createTime;

    /** 最后修改时间，UTC */
    private Instant updateTime;

    /** 能力明细，按能力代码升序 */
    private List<MidCapabilityDetailResponseBody> details;
}

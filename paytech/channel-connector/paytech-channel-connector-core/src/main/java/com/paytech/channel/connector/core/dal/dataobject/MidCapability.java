package com.paytech.payment.channel.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;
import com.paytech.payment.channel.core.enums.CapabilityStatus;


/** MidCapability 对应的 Channel 所属持久化对象。 */
@Getter
@Setter
public class MidCapability {
    /** 完整条件组合的能力组标识。 */
    private Long midCapabilityId;

    /** 所属 MID 标识。 */
    private Long midId;

    /** 精确支付方式目录代码，不允许通配符。 */
    private String paymentMethodCode;

    /** 支付体系代码或通配符 *。 */
    private String schemeCode;

    /** 消费者国家或通配符 *。 */
    private String countryCode;

    /** 交易币种或通配符 *。 */
    private String currency;

    /** 精确四位业务 MCC，不允许通配符。 */
    private String mccCode;

    /** 整组启停状态。 */
    private CapabilityStatus capabilityStatus;

    /** 创建 UTC 时间，微秒精度。 */
    private Instant createTime;

    /** 最后变更 UTC 时间，微秒精度。 */
    private Instant updateTime;
}

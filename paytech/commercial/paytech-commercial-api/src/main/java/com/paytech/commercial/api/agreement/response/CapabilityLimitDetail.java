package com.paytech.commercial.api.agreement.response;

import lombok.Getter;
import lombok.Setter;

/** 开通项约束的一个值。 */
@Getter
@Setter
public class CapabilityLimitDetail {
    /** 约束类别：CURRENCY、CONSUMER_COUNTRY、REQUIRE_3DS */
    private String limitCode;

    /** 规范化值 */
    private String limitValue;
}

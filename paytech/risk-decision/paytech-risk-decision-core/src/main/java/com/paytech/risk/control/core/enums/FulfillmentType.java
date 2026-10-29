package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Client 申报的交付方式，决定估算敞口的风险天数。 */
public enum FulfillmentType implements CodeEnum {
    /** 即时交付。 */
    INSTANT("INSTANT"),

    /** 预售，按交付天数计算。 */
    PRE_ORDER("PRE_ORDER"),

    /** 订阅，按一个计费周期 30 天计算。 */
    SUBSCRIPTION("SUBSCRIPTION"),

    /** 事后履约的服务。 */
    SERVICE("SERVICE");

    private final String code;

    FulfillmentType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}

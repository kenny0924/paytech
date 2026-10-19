package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 开通项约束类别（t_agreement_capability_limit.limit_code）；同码多行按 OR，异码按 AND。 */
public enum CapabilityLimitCode implements CodeEnum {
    /** 交易币种，值为 ISO 4217 */
    CURRENCY("CURRENCY"),

    /** 消费者国家，值为 ISO 3166-1 alpha-2 */
    CONSUMER_COUNTRY("CONSUMER_COUNTRY"),

    /** 要求 3DS，值固定为 1 */
    REQUIRE_3DS("REQUIRE_3DS");

    private final String code;

    CapabilityLimitCode(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}

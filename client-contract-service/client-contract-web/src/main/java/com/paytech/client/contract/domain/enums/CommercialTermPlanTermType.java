package com.paytech.client.contract.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** CommercialTermPlan TermType 的数据库闭合值域。 */
public enum CommercialTermPlanTermType implements CodeEnum {
    CLIENT_PRICING("CLIENT_PRICING"),

    PARTNER_SHARE("PARTNER_SHARE"),

    PLATFORM_SERVICE_CHARGE("PLATFORM_SERVICE_CHARGE");
    private final String code;
    CommercialTermPlanTermType(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}

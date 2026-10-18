package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 币种规则状态（t_currency_spec.status）；只有 ACTIVE 允许被新业务使用。HTTP 字段使用 String。 */
public enum CurrencySpecStatus implements CodeEnum {
    /** 草稿，未生效。 */
    DRAFT("DRAFT"),

    /** 允许新业务使用。 */
    ACTIVE("ACTIVE"),

    /** 已停用，不允许新业务使用。 */
    INACTIVE("INACTIVE");

    private final String code;

    CurrencySpecStatus(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }
}

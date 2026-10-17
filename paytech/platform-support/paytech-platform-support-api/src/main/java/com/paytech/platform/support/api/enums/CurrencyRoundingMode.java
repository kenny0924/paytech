package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 币种金额舍入方式（t_currency_spec.rounding_mode）；HTTP 字段使用 String。 */
public enum CurrencyRoundingMode implements CodeEnum {
    /** 四舍五入。 */
    HALF_UP("HALF_UP");

    private final String code;

    CurrencyRoundingMode(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }
}

package com.paytech.commercial.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** CommercialTermComponent BandMode 的数据库闭合值域。 */
public enum CommercialTermComponentBandMode implements CodeEnum {
    WHOLE_BASE("WHOLE_BASE"),

    PROGRESSIVE("PROGRESSIVE");
    private final String code;
    CommercialTermComponentBandMode(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}

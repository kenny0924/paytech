package com.paytech.risk.control.domain.underwriting;

import com.paytech.foundation.common.enums.CodeEnum;

/** 估算敞口无法计算的原因。 */
public enum ExposureUnavailableReason implements CodeEnum {
    /** 未申报预计月流水或币种。 */
    VOLUME_MISSING("VOLUME_MISSING"),

    /** 存量申请未采集交付方式。 */
    FULFILLMENT_TYPE_MISSING("FULFILLMENT_TYPE_MISSING"),

    /** 预售未填交付天数。 */
    FULFILLMENT_DAYS_MISSING("FULFILLMENT_DAYS_MISSING");

    private final String code;

    ExposureUnavailableReason(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}

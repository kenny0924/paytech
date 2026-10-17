package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** OperatingEntityApplication Status 的数据库闭合值域。 */
public enum OperatingEntityApplicationStatus implements CodeEnum {
    DRAFT("DRAFT"),

    SUBMITTED("SUBMITTED"),

    UNDER_REVIEW("UNDER_REVIEW"),

    APPROVED("APPROVED"),

    REJECTED("REJECTED"),

    WITHDRAWN("WITHDRAWN");
    private final String code;
    OperatingEntityApplicationStatus(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}

package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** OperatingEntity Status 的数据库闭合值域。 */
public enum OperatingEntityStatus implements CodeEnum {
    PENDING("PENDING"),

    ACTIVE("ACTIVE"),

    SUSPENDED("SUSPENDED"),

    CLOSED("CLOSED");
    private final String code;
    OperatingEntityStatus(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}

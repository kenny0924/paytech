package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** ClientEntityAffiliation Status 的数据库闭合值域。 */
public enum ClientEntityAffiliationStatus implements CodeEnum {
    ACTIVE("ACTIVE"),

    ENDED("ENDED");
    private final String code;
    ClientEntityAffiliationStatus(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}

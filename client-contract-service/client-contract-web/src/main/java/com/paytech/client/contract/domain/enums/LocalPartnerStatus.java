package com.paytech.client.contract.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** LocalPartner Status 的数据库闭合值域。 */
public enum LocalPartnerStatus implements CodeEnum {
    PENDING("PENDING"),

    ACTIVE("ACTIVE"),

    SUSPENDED("SUSPENDED"),

    TERMINATED("TERMINATED");
    private final String code;
    LocalPartnerStatus(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}

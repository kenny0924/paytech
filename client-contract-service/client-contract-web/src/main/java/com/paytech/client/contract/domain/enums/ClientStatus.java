package com.paytech.client.contract.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Client Status 的数据库闭合值域。 */
public enum ClientStatus implements CodeEnum {
    PENDING("PENDING"),

    ACTIVE("ACTIVE"),

    SUSPENDED("SUSPENDED"),

    TERMINATED("TERMINATED");
    private final String code;
    ClientStatus(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}

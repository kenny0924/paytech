package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** SellerDisclosure Status 的数据库闭合值域。 */
public enum SellerDisclosureStatus implements CodeEnum {
    DRAFT("DRAFT"),

    APPROVED("APPROVED"),

    ACTIVE("ACTIVE"),

    EXPIRED("EXPIRED"),

    WITHDRAWN("WITHDRAWN");
    private final String code;
    SellerDisclosureStatus(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}

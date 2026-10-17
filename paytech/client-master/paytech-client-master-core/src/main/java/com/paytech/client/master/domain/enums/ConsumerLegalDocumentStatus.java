package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** ConsumerLegalDocument Status 的数据库闭合值域。 */
public enum ConsumerLegalDocumentStatus implements CodeEnum {
    DRAFT("DRAFT"),

    APPROVED("APPROVED"),

    ACTIVE("ACTIVE"),

    EXPIRED("EXPIRED"),

    WITHDRAWN("WITHDRAWN");
    private final String code;
    ConsumerLegalDocumentStatus(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}

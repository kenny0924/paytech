package com.paytech.commercial.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** CommercialTermVersion Status 的数据库闭合值域。 */
public enum CommercialTermVersionStatus implements CodeEnum {
    DRAFT("DRAFT"),

    /** 已提交等待审批；提交人不得审批。 */
    PENDING_APPROVAL("PENDING_APPROVAL"),

    APPROVED("APPROVED"),

    SCHEDULED("SCHEDULED"),

    ACTIVE("ACTIVE"),

    SUPERSEDED("SUPERSEDED"),

    EXPIRED("EXPIRED");
    private final String code;
    CommercialTermVersionStatus(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}

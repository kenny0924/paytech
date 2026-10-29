package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 快照时的 Client 类型。 */
public enum UnderwritingClientType implements CodeEnum {
    /** 个人。 */
    INDIVIDUAL("INDIVIDUAL"),

    /** 机构。 */
    ORGANIZATION("ORGANIZATION");

    private final String code;

    UnderwritingClientType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}

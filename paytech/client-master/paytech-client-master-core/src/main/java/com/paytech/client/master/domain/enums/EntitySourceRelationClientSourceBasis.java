package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** EntitySourceRelation ClientSourceBasis 的数据库闭合值域。 */
public enum EntitySourceRelationClientSourceBasis implements CodeEnum {
    SAME_PARTY("SAME_PARTY"),

    VERIFIED_ASSOCIATION("VERIFIED_ASSOCIATION");
    private final String code;
    EntitySourceRelationClientSourceBasis(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}

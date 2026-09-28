package com.paytech.client.contract.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** EntitySourceRelation Status 的数据库闭合值域。 */
public enum EntitySourceRelationStatus implements CodeEnum {
    ACTIVE("ACTIVE"),

    ENDED("ENDED");
    private final String code;
    EntitySourceRelationStatus(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}

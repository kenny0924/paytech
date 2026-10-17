package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** EntitySourceRelation SourceType 的数据库闭合值域。 */
public enum EntitySourceRelationSourceType implements CodeEnum {
    PLATFORM("PLATFORM"),

    CLIENT("CLIENT"),

    PARTNER("PARTNER");
    private final String code;
    EntitySourceRelationSourceType(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}

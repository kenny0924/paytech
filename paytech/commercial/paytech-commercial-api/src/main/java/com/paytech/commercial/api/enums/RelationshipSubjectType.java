package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 商业关系快照角色绑定的主体类型（relationshipSnapshot.bindings[].subjectType）
 * <li>CLIENT 的 subjectId 为 clientId，OPERATING_ENTITY 为 entityNo（如 AE001），LOCAL_PARTNER 为 localPartnerId；PLATFORM 的
 * subjectId 为 null。</li>
 */
public enum RelationshipSubjectType implements CodeEnum {
    /** 平台 Client */
    CLIENT("CLIENT"),

    /** Operating Entity */
    OPERATING_ENTITY("OPERATING_ENTITY"),

    /** Local Partner */
    LOCAL_PARTNER("LOCAL_PARTNER"),

    /** 平台自身 */
    PLATFORM("PLATFORM");

    private final String code;

    RelationshipSubjectType(String code) {
        this.code = code;
    }

    /** 接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}

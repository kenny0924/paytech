package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 采集 Schema 字段适用的受审主体。 */
public enum SchemaSubject implements CodeEnum {
    /** 申请主主体。 */
    PRIMARY,

    /** 组织关联人员。 */
    RELATED_PERSON,

    /** 两者都适用。 */
    BOTH;

    /** 稳定 Code，等于常量名。 */
    @Override
    public String getCode() {
        return name();
    }

    /**
     * 按 Code 解析
     *
     * @param code 待解析的 Code
     * @return 对应常量；不认识或为空时返回 null，由调用方按自身合同处理
     */
    public static SchemaSubject fromCode(String code) {
        for (SchemaSubject value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}

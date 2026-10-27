package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 采集 Schema 字段的提交要求级别。 */
public enum RequirementLevel implements CodeEnum {
    /** 提交前必填。 */
    REQUIRED,

    /** 批准前必填，允许先提交。 */
    EVENTUALLY,

    /** 可选。 */
    OPTIONAL,

    /** 不展示、不校验。 */
    HIDDEN;

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
    public static RequirementLevel fromCode(String code) {
        for (RequirementLevel value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}

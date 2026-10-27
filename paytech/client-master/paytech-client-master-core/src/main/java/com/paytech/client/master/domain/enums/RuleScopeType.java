package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 规则开关的作用层级。 */
public enum RuleScopeType implements CodeEnum {
    /** 辖区。 */
    JURISDICTION,

    /** 组织类型，scope_code 为 辖区码:类型码。 */
    ORGANIZATION_TYPE,

    /** 标识类型。 */
    IDENTIFIER_TYPE;

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
    public static RuleScopeType fromCode(String code) {
        for (RuleScopeType value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}

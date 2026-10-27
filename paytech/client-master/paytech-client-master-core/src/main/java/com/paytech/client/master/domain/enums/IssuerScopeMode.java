package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 法律标识唯一性作用域的确定方式。 */
public enum IssuerScopeMode implements CodeEnum {
    /** 全局（GLOBAL）。 */
    NONE,

    /** 按签发国。 */
    COUNTRY,

    /** 按组织登记机关。 */
    AUTHORITY;

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
    public static IssuerScopeMode fromCode(String code) {
        for (IssuerScopeMode value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}

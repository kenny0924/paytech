package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 扩展属性的敏感级别。 */
public enum AttributeSensitivity implements CodeEnum {
    /** 公开。 */
    PUBLIC,

    /** 内部。 */
    INTERNAL,

    /** 须进 Vault；本期不支持写入。 */
    VAULT;

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
    public static AttributeSensitivity fromCode(String code) {
        for (AttributeSensitivity value : values()) {
            if (value.name().equals(code)) {
                return value;
            }
        }
        return null;
    }
}

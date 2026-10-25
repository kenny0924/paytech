package com.paytech.acquiring.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Order 法律经营主体的来源类型；平台自有 OE 也经“我们的 PayFac”Partner 以 PARTNER 来源提供（2026-10-24 删除 PLATFORM 来源）。 */
public enum EntitySourceType implements CodeEnum {
    /** Organization Client 自有或受控的 OE */
    CLIENT("CLIENT"),

    /** Local Partner 提供的 OE */
    PARTNER("PARTNER");

    private final String code;

    EntitySourceType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }

    /** 从跨边界稳定 code 解析内部枚举，不依赖 Java 常量名。 */
    public static EntitySourceType fromCode(String code) {
        for (EntitySourceType value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Entity Source Type code 不受支持");
    }
}

package com.paytech.channel.connector.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 字段值的数据类型；金额与精确小数以 STRING 传递。 */
public enum ApiFieldDataType implements CodeEnum {

    /** 字符串。 */
    STRING("STRING"),

    /** 布尔值。 */
    BOOLEAN("BOOLEAN"),

    /** 整数。 */
    INTEGER("INTEGER"),

    /** 十进制数。 */
    DECIMAL("DECIMAL"),

    /** 对象容器。 */
    OBJECT("OBJECT"),

    /** 数组容器，元素用 [] 定义。 */
    ARRAY("ARRAY");

    private final String code;

    /** 固定发布代码，不依赖 Java 常量名。 */
    ApiFieldDataType(String code) {
        this.code = code;
    }

    /** 返回稳定代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 按稳定代码解析；未知代码返回 null，由调用方按入口语义拒绝。 */
    public static ApiFieldDataType fromCode(String code) {
        for (ApiFieldDataType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}

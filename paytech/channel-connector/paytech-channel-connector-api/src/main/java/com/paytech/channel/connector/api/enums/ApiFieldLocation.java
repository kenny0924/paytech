package com.paytech.channel.connector.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 字段在 HTTP 报文中的位置；响应只允许 HEADER、BODY。 */
public enum ApiFieldLocation implements CodeEnum {

    /** HTTP Header，路径即参数名，大小写不敏感。 */
    HEADER("HEADER"),

    /** 报文体，路径为点分完整路径。 */
    BODY("BODY"),

    /** 查询参数。 */
    QUERY("QUERY"),

    /** 路径变量。 */
    PATH("PATH");

    private final String code;

    /** 固定发布代码，不依赖 Java 常量名。 */
    ApiFieldLocation(String code) {
        this.code = code;
    }

    /** 返回稳定代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 按稳定代码解析；未知代码返回 null，由调用方按入口语义拒绝。 */
    public static ApiFieldLocation fromCode(String code) {
        for (ApiFieldLocation value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}

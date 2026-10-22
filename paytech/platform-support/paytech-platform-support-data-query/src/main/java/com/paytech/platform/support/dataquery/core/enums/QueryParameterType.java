package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 查询模板参数类型；值的 JSON 形态与约束见技术方案第 3.2 节。 */
@Getter
@RequiredArgsConstructor
public enum QueryParameterType implements CodeEnum {

    /** 字符串，可带 maxLength、allowedValues。 */
    STRING("STRING"),

    /** 64 位整数，JSON 字符串或整数，可带 min、max。 */
    INTEGER("INTEGER"),

    /** 十进制字符串，可带 min、max。 */
    DECIMAL("DECIMAL"),

    /** 布尔值。 */
    BOOLEAN("BOOLEAN"),

    /** ISO-8601 日期，格式 YYYY-MM-DD，可带 min、max。 */
    DATE("DATE"),

    /** ISO-8601 本地日期时间，不带时区，可带 min、max。 */
    DATETIME("DATETIME"),

    /** 枚举字符串，allowedValues 必填。 */
    ENUM("ENUM"),

    /** 片段参数：取值为 choices 的键，输出对应的固定片段原文，不绑定。 */
    FRAGMENT("FRAGMENT"),

    /** 集合，elementType 必填且不能是 FRAGMENT 或 LIST，可带 maxSize。 */
    LIST("LIST"),

    /** 对象，只能作为 LIST 的 elementType，字段由 fields 声明。 */
    OBJECT("OBJECT");

    private final String code;
}

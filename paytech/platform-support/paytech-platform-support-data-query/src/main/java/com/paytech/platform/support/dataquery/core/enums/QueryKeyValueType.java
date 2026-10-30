package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 联合查询键类型的值类型（t_query_key_type.value_type）；决定入口值规范化与可绑定的参数类型。 */
@Getter
@RequiredArgsConstructor
public enum QueryKeyValueType implements CodeEnum {

    /** 64 位非负整数，规范值去前导零；可绑定 INTEGER 或 STRING 参数（含 LIST）。 */
    INTEGER("INTEGER"),

    /** 字符串原样比较，不 trim、不改大小写；只能绑定 STRING 参数（含 LIST）。 */
    STRING("STRING");

    private final String code;
}

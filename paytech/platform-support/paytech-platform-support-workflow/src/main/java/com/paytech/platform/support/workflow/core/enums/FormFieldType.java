package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 启动表单与表单动作共用的字段类型。 */
@Getter
@RequiredArgsConstructor
public enum FormFieldType implements CodeEnum {

    /** 单行文本，最长 255。 */
    TEXT("TEXT"),

    /** 多行文本，最长 4000。 */
    LONG_TEXT("LONG_TEXT"),

    /** 十进制字符串，可带 min、max、scale。 */
    NUMBER("NUMBER"),

    /** 布尔值。 */
    BOOLEAN("BOOLEAN"),

    /** 枚举，取值必须是配置的选项之一。 */
    ENUM("ENUM"),

    /** 日期，格式 YYYY-MM-DD。 */
    DATE("DATE"),

    /** 文件地址（/files/v1/{fileId}）数组，可多份。 */
    FILE("FILE");

    private final String code;
}

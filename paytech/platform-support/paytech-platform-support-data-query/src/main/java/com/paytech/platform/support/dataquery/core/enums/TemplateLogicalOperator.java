package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 模板条件表达式的逻辑运算符；编码即模板中的符号。 */
@Getter
@RequiredArgsConstructor
public enum TemplateLogicalOperator implements CodeEnum {

    /** 与，短路求值。 */
    AND("&&"),

    /** 或，短路求值。 */
    OR("||");

    private final String code;
}

package com.paytech.platform.support.dataquery.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 调用方参数的字段级错误，field 形如 params.conditions[2].minAmount。 */
@Getter
@AllArgsConstructor
public final class ParameterError {

    /** 字段路径。 */
    private final String field;

    /** 中文原因，不含提交的原值。 */
    private final String message;
}

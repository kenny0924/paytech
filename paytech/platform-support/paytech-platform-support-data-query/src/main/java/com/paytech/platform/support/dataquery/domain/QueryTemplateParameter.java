package com.paytech.platform.support.dataquery.domain;

import com.paytech.platform.support.dataquery.core.enums.QueryParameterType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板签名中的一个参数。 */
@Getter
@AllArgsConstructor
public final class QueryTemplateParameter {

    /** 参数名。 */
    private final String name;

    /** 参数类型。 */
    private final QueryParameterType type;

    /**
     * 调用方是否必须提供
     * <li>声明为必填且没有默认值时为 true。</li>
     */
    private final boolean required;
}

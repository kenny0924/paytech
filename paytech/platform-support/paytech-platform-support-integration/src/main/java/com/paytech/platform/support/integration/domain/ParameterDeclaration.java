package com.paytech.platform.support.integration.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 参数或输出的声明：公共脚本参数、原子 API 调用参数与输出、注册函数参数共用。 */
@Getter
@AllArgsConstructor
public class ParameterDeclaration {

    /** 参数名，[a-z][A-Za-z0-9]{0,63}。 */
    private final String name;

    /** 数据类型：字段类型之一或 PROTECTED_HANDLE。 */
    private final String dataType;

    /** 是否必填。 */
    private final boolean required;
}

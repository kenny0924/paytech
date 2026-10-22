package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 动作输入参数的取值来源。 */
@Getter
@RequiredArgsConstructor
public enum InputMappingSource implements CodeEnum {

    /** 取实例变量，value 为变量名。 */
    VARIABLE("VARIABLE"),

    /** 取常量，value 为常量值。 */
    CONSTANT("CONSTANT");

    private final String code;
}

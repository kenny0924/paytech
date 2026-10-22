package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 模板校验问题级别；只有 ERROR 阻止保存。 */
@Getter
@RequiredArgsConstructor
public enum TemplateProblemLevel implements CodeEnum {

    /** 错误，不能保存或执行。 */
    ERROR("ERROR"),

    /** 警告，例如声明了但未使用的参数，不阻止保存。 */
    WARNING("WARNING"),

    /** 提示，例如组合过多改用覆盖集校验。 */
    INFO("INFO");

    private final String code;
}

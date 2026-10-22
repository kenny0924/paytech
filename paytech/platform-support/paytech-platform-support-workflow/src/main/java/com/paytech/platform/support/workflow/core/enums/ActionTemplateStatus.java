package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 动作模板状态。 */
@Getter
@RequiredArgsConstructor
public enum ActionTemplateStatus implements CodeEnum {

    /** 可被新发布的流程引用。 */
    ACTIVE("ACTIVE"),

    /** 停用：新发布不能引用，已发布版本照常运行。 */
    INACTIVE("INACTIVE");

    private final String code;
}

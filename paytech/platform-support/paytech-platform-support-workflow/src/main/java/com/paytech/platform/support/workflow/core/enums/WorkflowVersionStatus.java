package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 流程版本状态，与 t_workflow_definition_version.status 一致。 */
@Getter
@RequiredArgsConstructor
public enum WorkflowVersionStatus implements CodeEnum {

    /** 草稿，可随保存覆盖。 */
    DRAFT("DRAFT"),

    /** 已发布，内容不可变。 */
    PUBLISHED("PUBLISHED");

    private final String code;
}

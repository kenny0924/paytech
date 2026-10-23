package com.paytech.platform.support.workflow.domain.query;

import lombok.Getter;
import lombok.Setter;

/** 一个动作模板被多少个流程定义的当前发布版本引用。 */
@Getter
@Setter
public class WorkflowTemplateReferenceCountView {

    /** 动作模板 ID。 */
    private Long templateId;

    /** 当前发布版本冻结了该模板的流程定义数（按定义去重）。 */
    private Integer definitionCount;
}

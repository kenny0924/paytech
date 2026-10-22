package com.paytech.platform.support.api.workflow.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 一条发布校验问题。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowProblemBody {

    /** 问题码。 */
    private String code;

    /** 相关节点；可空。 */
    private String nodeKey;

    /** 相关连线；可空。 */
    private String edgeKey;

    /** 节点或表单内的条目（动作、字段、规则序号）；可空。 */
    private String itemKey;

    /** 中文说明。 */
    private String message;
}

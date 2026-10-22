package com.paytech.platform.support.workflow.domain.validation;

import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;

/**
 * 一条发布校验或配置校验问题；画布按 nodeKey / edgeKey 标红，属性面板按 itemKey 定位。
 *
 * @param code 问题码
 * @param nodeKey 相关节点 key；与节点无关时为 null
 * @param edgeKey 相关连线 key；与连线无关时为 null
 * @param itemKey 节点内或表单内的条目：动作 key、字段 key、规则序号等；没有时为 null
 * @param message 中文说明
 */
public record WorkflowGraphProblem(WorkflowProblemCode code, String nodeKey, String edgeKey, String itemKey,
    String message) {
}

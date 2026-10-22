package com.paytech.platform.support.workflow.domain.action;

import com.paytech.platform.support.workflow.core.enums.ActionTemplateStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;

/**
 * 发布校验所需的动作模板事实，由调用方从 t_workflow_action_template 读取后提供。
 *
 * @param templateId 模板 ID
 * @param templateVersion 模板并发版本，发布时冻结进快照
 * @param actionType 动作类型
 * @param status 模板状态
 * @param config 解析后的配置
 */
public record ActionTemplateReference(Long templateId, int templateVersion, WorkflowActionType actionType,
    ActionTemplateStatus status, ActionConfig config) {
}

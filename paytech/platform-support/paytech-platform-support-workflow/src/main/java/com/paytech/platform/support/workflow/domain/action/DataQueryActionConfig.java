package com.paytech.platform.support.workflow.domain.action;

import java.util.List;
import java.util.Set;

import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;

/**
 * DATA_QUERY 动作配置（预留）：本期只保存 data-service 查询编码与原始配置，不能被发布的流程引用，因此不声明输出与输入。
 *
 * @param queryCode data-service 维护的查询编码；可能为空
 * @param rawConfigJson 原始 config_json，供以后实现 parameters、passRule、evidenceColumns 时解析
 */
public record DataQueryActionConfig(String queryCode, String rawConfigJson) implements ActionConfig {

    /** 本配置固定对应的动作类型。 */
    @Override
    public WorkflowActionType actionType() {
        return WorkflowActionType.DATA_QUERY;
    }

    /** 预留类型，不提供输出。 */
    @Override
    public List<VariableDeclaration> outputs(String actionKey) {
        return List.of();
    }

    /** 预留类型，不接受输入映射。 */
    @Override
    public Set<String> inputKeys() {
        return Set.of();
    }
}

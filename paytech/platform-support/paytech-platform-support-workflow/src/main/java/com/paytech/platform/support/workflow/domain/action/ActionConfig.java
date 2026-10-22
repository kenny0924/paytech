package com.paytech.platform.support.workflow.domain.action;

import java.util.List;
import java.util.Set;

import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;

/**
 * 动作模板 config_json 解析后的配置，按动作类型区分。
 */
public sealed interface ActionConfig permits FormActionConfig, FileUploadActionConfig, PageTaskActionConfig,
    ManualConfirmActionConfig, DataQueryActionConfig {

    /**
     * 配置对应的动作类型。
     *
     * @return 动作类型
     */
    WorkflowActionType actionType();

    /**
     * 动作验证通过后对外提供的输出变量。
     *
     * @param actionKey 节点上引用该模板的动作 key，作为变量前缀
     * @return 输出变量声明
     */
    List<VariableDeclaration> outputs(String actionKey);

    /**
     * 节点动作 inputs 可以映射的参数 key。
     *
     * @return 可映射的 key；不接受输入时为空集合
     */
    Set<String> inputKeys();
}

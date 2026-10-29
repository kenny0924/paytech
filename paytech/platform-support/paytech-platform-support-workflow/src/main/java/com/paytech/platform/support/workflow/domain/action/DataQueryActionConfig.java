package com.paytech.platform.support.workflow.domain.action;

import java.util.List;
import java.util.Set;

import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;

/**
 * DATA_QUERY 动作配置（REQ-05，technique 8.4）：引用一个 data-query 模板，由系统按单行约定（satisfied / out_* / missing）
 * 检查并自动完成；没有人工提交入口。
 * <li>参数取值在流程节点动作的 inputs 中映射（VARIABLE / CONSTANT），模板这里只列出参数 key。</li>
 * <li>只写声明的输出，变量名为 {@code <actionKey>.<name>}。</li>
 *
 * @param queryCode data-query 模板编码
 * @param parameters 节点上可映射的查询参数 key
 * @param outputs 声明的输出
 * @param descriptionKey 完成标准文案键；可空
 * @param instruction 完成标准说明（文案键无译文时显示）；可空
 */
public record DataQueryActionConfig(String queryCode, List<String> parameters, List<DataQueryOutput> outputs,
    String descriptionKey, String instruction) implements ActionConfig {

    /** 复制列表，保持不可变。 */
    public DataQueryActionConfig {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }

    /** 本配置固定对应的动作类型。 */
    @Override
    public WorkflowActionType actionType() {
        return WorkflowActionType.DATA_QUERY;
    }

    /** 每个声明的输出一个变量。 */
    @Override
    public List<VariableDeclaration> outputs(String actionKey) {
        return outputs.stream().map(output -> new VariableDeclaration(actionKey + "." + output.name(), output.type(),
            output.enumValues())).toList();
    }

    /** 节点可为声明的查询参数映射取值。 */
    @Override
    public Set<String> inputKeys() {
        return Set.copyOf(parameters);
    }
}

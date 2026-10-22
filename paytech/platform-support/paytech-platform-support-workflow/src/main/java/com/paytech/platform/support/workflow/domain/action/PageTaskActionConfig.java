package com.paytech.platform.support.workflow.domain.action;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;

/**
 * PAGE_TASK 动作配置：跳到控制台内部路由完成操作，执行人确认后输出 {@code <actionKey>.note}。
 *
 * @param pageRoute 以 / 开头的控制台内部路由，不接受外部 URL
 * @param parameters 跳转时携带的参数，由节点动作 inputs 映射取值
 * @param instruction 给执行人的说明；可能为空
 */
public record PageTaskActionConfig(String pageRoute, List<PageParameter> parameters, String instruction)
    implements ActionConfig {

    /** 输出变量的属性名。 */
    public static final String NOTE_OUTPUT = "note";

    /** 复制列表，保持不可变。 */
    public PageTaskActionConfig {
        parameters = List.copyOf(parameters);
    }

    /** 本配置固定对应的动作类型。 */
    @Override
    public WorkflowActionType actionType() {
        return WorkflowActionType.PAGE_TASK;
    }

    /** 输出执行人的确认说明。 */
    @Override
    public List<VariableDeclaration> outputs(String actionKey) {
        return List.of(VariableDeclaration.of(actionKey + "." + NOTE_OUTPUT, FormFieldType.LONG_TEXT));
    }

    /** 可映射声明的跳转参数。 */
    @Override
    public Set<String> inputKeys() {
        Set<String> keys = new LinkedHashSet<>();
        parameters.stream().map(PageParameter::key).forEach(keys::add);
        return keys;
    }
}

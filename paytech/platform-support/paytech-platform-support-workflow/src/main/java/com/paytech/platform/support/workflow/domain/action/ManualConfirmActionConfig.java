package com.paytech.platform.support.workflow.domain.action;

import java.util.List;
import java.util.Set;

import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;

/**
 * MANUAL_CONFIRM 动作配置：执行人确认已完成，输出 {@code <actionKey>.note}。
 *
 * @param instruction 给执行人的说明；可能为空
 * @param noteRequired 存量配置的“说明必填”；2026-10-22 起忽略，说明一律选填
 */
public record ManualConfirmActionConfig(String instruction, boolean noteRequired) implements ActionConfig {

    /** 输出变量的属性名。 */
    public static final String NOTE_OUTPUT = "note";

    /** 本配置固定对应的动作类型。 */
    @Override
    public WorkflowActionType actionType() {
        return WorkflowActionType.MANUAL_CONFIRM;
    }

    /** 输出执行人的确认说明。 */
    @Override
    public List<VariableDeclaration> outputs(String actionKey) {
        return List.of(VariableDeclaration.of(actionKey + "." + NOTE_OUTPUT, FormFieldType.LONG_TEXT));
    }

    /** 不接受输入映射。 */
    @Override
    public Set<String> inputKeys() {
        return Set.of();
    }
}

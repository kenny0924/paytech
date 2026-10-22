package com.paytech.platform.support.workflow.domain.action;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;
import com.paytech.platform.support.workflow.domain.form.FormField;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;

/**
 * FORM 动作配置：按字段填写，输出 {@code <actionKey>.<field>}；输入映射为字段的默认值。
 *
 * @param form 字段定义
 */
public record FormActionConfig(FormDefinition form) implements ActionConfig {

    /** 本配置固定对应的动作类型。 */
    @Override
    public WorkflowActionType actionType() {
        return WorkflowActionType.FORM;
    }

    /** 每个字段一个输出变量。 */
    @Override
    public List<VariableDeclaration> outputs(String actionKey) {
        return VariableDeclaration.ofForm(actionKey, form);
    }

    /** 可为任一字段映射默认值。 */
    @Override
    public Set<String> inputKeys() {
        Set<String> keys = new LinkedHashSet<>();
        form.fields().stream().map(FormField::key).forEach(keys::add);
        return keys;
    }
}

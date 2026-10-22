package com.paytech.platform.support.workflow.domain.form;

import java.util.List;

/**
 * 表单定义：start_form_json 与 FORM 动作配置共用的字段列表。
 *
 * @param fields 字段，按显示顺序
 */
public record FormDefinition(List<FormField> fields) {

    /** 没有字段的表单。 */
    public static final FormDefinition EMPTY = new FormDefinition(List.of());

    /** 复制列表，保持不可变。 */
    public FormDefinition {
        fields = List.copyOf(fields);
    }
}

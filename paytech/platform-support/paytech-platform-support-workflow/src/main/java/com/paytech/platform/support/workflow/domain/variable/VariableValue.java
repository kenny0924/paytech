package com.paytech.platform.support.workflow.domain.variable;

import com.paytech.platform.support.workflow.core.enums.FormFieldType;

import tools.jackson.databind.JsonNode;

/**
 * 一个实例变量的当前值：声明类型与取自 start_values_json 或 output_json 的原始 JSON 值。
 *
 * @param type 声明类型
 * @param value 原始值；未提交或缺失时为 null
 */
public record VariableValue(FormFieldType type, JsonNode value) {

    /**
     * 是否为空：缺失、null、空字符串或空数组。
     *
     * @return 为空返回 true
     */
    public boolean isEmpty() {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return true;
        }
        if (value.isString()) {
            return value.asString().isEmpty();
        }
        return value.isArray() && value.isEmpty();
    }
}

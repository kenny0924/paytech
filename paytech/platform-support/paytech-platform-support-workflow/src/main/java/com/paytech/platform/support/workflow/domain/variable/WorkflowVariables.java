package com.paytech.platform.support.workflow.domain.variable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.JsonNode;

/**
 * 实例变量的运行时视图。变量不单独建表（技术方案 3.3）：start.* 取实例的 start_values_json，
 * {@code <actionKey>.*} 取该动作最新一轮 COMPLETED 动作运行的 output_json，由调用方按声明组装。
 */
public final class WorkflowVariables {

    /** 没有任何变量。 */
    public static final WorkflowVariables EMPTY = new WorkflowVariables(Map.of());

    private final Map<String, VariableValue> values;

    /**
     * 以变量名到值的映射构造。
     *
     * @param values 变量值
     */
    private WorkflowVariables(Map<String, VariableValue> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * 开始组装变量。
     *
     * @return 组装器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 按名称取变量值。
     *
     * @param name 完整变量名
     * @return 变量值；未声明时为空
     */
    public Optional<VariableValue> get(String name) {
        return Optional.ofNullable(values.get(name));
    }

    /** 实例变量组装器。 */
    public static final class Builder {

        private final Map<String, VariableValue> values = new LinkedHashMap<>();

        /**
         * 按声明从一个 JSON 对象取值：变量的属性名即声明名去掉前缀后的部分。
         *
         * @param declarations 变量声明，如启动表单字段或某动作的输出
         * @param object start_values_json 或 output_json；尚无值时为 null
         * @return 组装器
         */
        public Builder add(List<VariableDeclaration> declarations, JsonNode object) {
            for (VariableDeclaration declaration : declarations) {
                JsonNode value = object == null ? null : object.get(declaration.localName());
                values.put(declaration.name(), new VariableValue(declaration.type(), value));
            }
            return this;
        }

        /**
         * 完成组装。
         *
         * @return 实例变量
         */
        public WorkflowVariables build() {
            return new WorkflowVariables(values);
        }
    }
}

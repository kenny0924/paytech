package com.paytech.platform.support.workflow.domainservice.process;

import java.util.Map;

import com.paytech.platform.support.workflow.core.enums.InputMappingSource;
import com.paytech.platform.support.workflow.domain.graph.InputMapping;
import com.paytech.platform.support.workflow.domain.variable.VariableValue;
import com.paytech.platform.support.workflow.domain.variable.WorkflowVariables;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * DATA_QUERY 查询参数：按节点动作 inputs 从当前实例变量取值（REQ-05，technique 10.1）。纯内存计算。
 * <li>VARIABLE 取变量当前值，变量尚无值时为 JSON null（由 data-query 的参数声明决定是否缺失）；CONSTANT 取常量文本。</li>
 */
public final class DataQueryParameterResolver {

    /**
     * 解析查询参数。
     *
     * @param inputs 节点动作的输入映射
     * @param variables 当前实例变量
     * @return 参数 JSON 对象，键为查询参数名
     */
    public ObjectNode resolve(Map<String, InputMapping> inputs, WorkflowVariables variables) {
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        inputs.forEach((key, mapping) -> params.set(key, value(mapping, variables)));
        return params;
    }

    /**
     * 一个参数的取值。
     *
     * @param mapping 输入映射
     * @param variables 当前实例变量
     * @return 取值；变量缺失时为 JSON null
     */
    JsonNode value(InputMapping mapping, WorkflowVariables variables) {
        if (mapping.source() == InputMappingSource.CONSTANT) {
            return mapping.value() == null ? JsonNodeFactory.instance.nullNode()
                : JsonNodeFactory.instance.stringNode(mapping.value());
        }
        return variables.get(mapping.value()).map(VariableValue::value).filter(value -> !value.isMissingNode())
            .orElse(JsonNodeFactory.instance.nullNode());
    }
}

package com.paytech.risk.decision.domain.query;

import java.util.Arrays;
import java.util.List;

import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.decision.domain.variable.DecisionSourceContracts;
import com.paytech.risk.decision.domain.variable.RiskVariable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 从 Risk 系统变量目录生成配置能力，不包含运行输入或结果。 */
@Getter
@AllArgsConstructor
public class FlowAuthoringContextView {
    /** 本期支持的准确输入合同。 */
    private final DecisionPointAuthoringDescriptor descriptor;

    /** 适用于流程条件的系统输入字段。 */
    private final List<Field> fields;

    /** 适用于流程条件的节点输出字段。 */
    private final List<Field> nodeOutputs;

    /** 仅根据已读目录事实投影，类型级运算能力由 Owner 定义。 */
    public static FlowAuthoringContextView from(DecisionPointAuthoringDescriptor descriptor,
        List<RiskVariable.Entry> definitions) {
        List<RiskVariable.Entry> selected = definitions.stream()
            .filter(e -> e.definition().usages().contains(VariableUsage.FLOW_CONDITION)).toList();
        return new FlowAuthoringContextView(descriptor,
            selected.stream().filter(e -> e.definition().sourceType() == VariableSource.INPUT).map(FlowAuthoringContextView::field).toList(),
            selected.stream().filter(e -> e.definition().sourceType() == VariableSource.NODE_OUTPUT).map(FlowAuthoringContextView::field).toList());
    }

    /** 来源合同定义必填性；变量目录拥有名称、类型、格式与编码。 */
    private static Field field(RiskVariable.Entry entry) {
        var definition = entry.definition();
        var source = DecisionSourceContracts.find(definition);
        var field = source.fields().stream().filter(f -> f.fieldKey().equals(definition.sourceField())).findFirst().orElseThrow();
        return new Field(source.nodeType(), definition.sourceField(), entry.variableName(), definition.valueType().getCode(),
            field.required(), operators(definition.valueType()), definition.formatCode().getCode(), definition.variableCode());
    }

    /** 可排序值类型支持序比较，其他类型保留原相等与集合运算。 */
    private static List<String> operators(DecisionValueType type) {
        return Arrays.stream(ComparisonOperator.values())
            .filter(op -> type == DecisionValueType.NUMBER || type == DecisionValueType.DATETIME || type == DecisionValueType.DURATION
                || !List.of("GT", "GTE", "LT", "LTE", "BETWEEN").contains(op.getCode())).map(ComparisonOperator::getCode).toList();
    }

    /** 系统来源字段的查询投影，输入变量没有节点类型。 */
    @Getter
    @AllArgsConstructor
    public static class Field {
        /** 节点输出所属节点类型；普通输入为空。 */
        private final String nodeType;
        /** 系统来源字段编码。 */
        private final String fieldCode;
        /** 变量展示名称。 */
        private final String label;
        /** 变量值类型编码。 */
        private final String valueType;
        /** 来源合同是否必填。 */
        private final boolean required;
        /** 可用运算符。 */
        private final List<String> operators;
        /** 变量格式约束。 */
        private final String literalDomain;
        /** 统一变量编码。 */
        private final String variableCode;
    }
}

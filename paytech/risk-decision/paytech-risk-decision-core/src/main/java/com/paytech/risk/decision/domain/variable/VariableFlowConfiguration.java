package com.paytech.risk.decision.domain.variable;

import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.flow.DecisionFlow.*;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 连线的变量引用遍历和纯条件规范化；旧格式仍交给原 Reader。 */
public final class VariableFlowConfiguration {
    private VariableFlowConfiguration() { }

    /** 从整棵表达式提取引用，不能依赖 JSON 文本搜索。 */
    public static Set<String> references(Graph graph) {
        Set<String> codes = new TreeSet<>();
        graph.edges().stream().filter(e -> e.condition() != null && e.condition().schemaVersion() == 2)
            .forEach(e -> references(e.condition().root(), codes));
        return Set.copyOf(codes);
    }

    /** 已通过树形状校验；每个谓词共用规则的来源/单位/值域校验器。 */
    public static Expression validate(Expression expression, VariableConditionValidator.Context context) {
        if (expression instanceof Group group) {
            return new Group(group.operator(), group.children().stream().map(child -> validate(child, context)).toList());
        }
        Predicate predicate = (Predicate) expression;
        List<VariableOperand> operands = VariableConditionValidator.validate(ComparisonOperator.valueOf(predicate.operator()), predicate.operands(), context);
        return new Predicate(null, null, predicate.operator(), null, operands);
    }

    private static void references(Expression expression, Set<String> codes) {
        if (expression instanceof Group group) group.children().forEach(child -> references(child, codes));
        else {
            Predicate predicate = (Predicate) expression;
            if (predicate.operands() == null) throw new IllegalArgumentException("统一变量条件缺少操作数");
            predicate.operands().forEach(o -> {
                if (o.variableCode() != null) codes.add(o.variableCode());
                if (o.currencyVariableCode() != null) codes.add(o.currencyVariableCode());
            });
        }
    }
}

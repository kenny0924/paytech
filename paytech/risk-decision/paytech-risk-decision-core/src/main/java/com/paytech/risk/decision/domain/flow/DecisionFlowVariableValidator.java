package com.paytech.risk.decision.domain.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Condition;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Edge;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Graph;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Issue;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Node;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domain.variable.RiskVariable;
import com.paytech.risk.decision.domain.variable.VariableConditionValidator;
import com.paytech.risk.decision.domain.variable.VariableFlowConfiguration;
import com.paytech.risk.decision.domain.variable.VariableRuleConfiguration;
import com.paytech.risk.decision.domainservice.service.DecisionFlowConfigurationException.Reason;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;

import lombok.AllArgsConstructor;

/** 根据调用方已加载的规则、变量和集合事实校验条件，不执行查询或求值。 */
@Component
@AllArgsConstructor
public class DecisionFlowVariableValidator {
    private final DecisionFlowJsonCodec codec;

    /** 校验原规则引用及每条连线，返回规范化条件和稳定问题顺序。 */
    public Result check(String pointCode, Graph graph, Map<Long, DecisionRuleSetAggregate> ruleSets,
        Map<String, RiskVariable.Entry> definitions, Map<String, List<RiskVariable.Member>> collections) {
        List<Issue> issues = new ArrayList<>();
        for (int index = 0; index < graph.nodes().size(); index++) {
            Node node = graph.nodes().get(index);
            if (!"RULE_SET".equals(node.type())) continue;
            try {
                VariableRuleConfiguration.validateReference(ruleSets.get(node.ruleSetVersionId()),
                    new VariableConditionValidator.Context(pointCode, VariableUsage.RULE_CONDITION, null, definitions, collections));
            } catch (RiskVariableException failure) {
                issues.add(new Issue(failure.reason().getCode(), node.nodeKey(), null, "graph.nodes[" + index + "].ruleSetVersionId", failure.getMessage()));
            }
        }
        Map<String, Node> nodes = graph.nodes().stream().collect(Collectors.toMap(Node::nodeKey, Function.identity()));
        List<Edge> edges = new ArrayList<>();
        for (int index = 0; index < graph.edges().size(); index++) {
            Edge edge = graph.edges().get(index);
            Condition condition = edge.condition();
            if (condition != null) {
                try {
                    condition = new Condition(2, VariableFlowConfiguration.validate(condition.root(), new VariableConditionValidator.Context(
                        pointCode, VariableUsage.FLOW_CONDITION, nodes.get(edge.sourceNodeKey()).type(), definitions, collections)));
                    if (codec.conditionBytes(condition).length > 8192) throw new RiskVariableException(RiskVariableException.Reason.INVALID_OPERAND, "规范条件超过存储容量");
                } catch (RiskVariableException failure) {
                    issues.add(new Issue(failure.reason().getCode(), null, edge.edgeKey(), "graph.edges[" + index + "].condition", failure.getMessage()));
                }
            }
            edges.add(new Edge(edge.edgeKey(), edge.sourceNodeKey(), edge.targetNodeKey(), edge.type(), edge.priority(), edge.name(), condition));
        }
        return new Result(new Graph(graph.schemaVersion(), graph.nodes(), edges), List.copyOf(issues));
    }

    /** 保存和静态检查共同拒绝把旧条件格式作为新内容。 */
    public static List<Issue> legacyIssues(Graph graph) {
        List<Issue> issues = new ArrayList<>();
        for (int index = 0; index < graph.edges().size(); index++) {
            Edge edge = graph.edges().get(index);
            if (edge.condition() != null && edge.condition().schemaVersion() != 2) {
                issues.add(new Issue("LEGACY_AUTHORING_NOT_SUPPORTED", null, edge.edgeKey(),
                    "graph.edges[" + index + "].condition", "请转换为统一变量条件后保存"));
            }
        }
        return List.copyOf(issues);
    }

    /** 纯校验结果，不包含持久化或执行步骤。 */
    public record Result(Graph graph, List<Issue> issues) { }
}

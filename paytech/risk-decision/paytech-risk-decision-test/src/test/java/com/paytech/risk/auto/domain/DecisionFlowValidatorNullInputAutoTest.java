package com.paytech.risk.auto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.risk.decision.api.evaluation.request.PaymentAttemptRiskInput;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry;
import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.FlowEdgeType;
import com.paytech.risk.decision.domain.enums.FlowNodeType;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Checked;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Condition;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Edge;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Graph;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Group;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Issue;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Node;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Predicate;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Save;
import com.paytech.risk.decision.domain.flow.DecisionFlowJsonCodec;
import com.paytech.risk.decision.domain.flow.DecisionFlowValidator;
import com.paytech.risk.decision.domain.flow.DecisionFlowValidatorImpl;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.BooleanLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidatorImpl;

/**
 * 决策流静态校验对空字段的防御（纯 Java 合同）
 * <li>HTTP 严格 JSON 已拒绝空的类型与运算符；校验器作为模块合同仍须以校验问题报告，不抛空指针。</li>
 *
 * @see DecisionFlowValidator#check(Save)
 */
class DecisionFlowValidatorNullInputAutoTest {
    private static final String START = FlowNodeType.START.getCode();
    private static final String RULE_SET = FlowNodeType.RULE_SET.getCode();
    private static final String END = FlowNodeType.END.getCode();
    private static final String UNCONDITIONAL = FlowEdgeType.UNCONDITIONAL.getCode();
    private static final String CONDITIONAL = FlowEdgeType.CONDITIONAL.getCode();
    private static final String ELSE = FlowEdgeType.ELSE.getCode();

    private final DecisionFlowValidator validator = new DecisionFlowValidatorImpl(
        new RuleAstValidatorImpl(DecisionActionRegistry.instance()), new DecisionFlowJsonCodec());

    /** 空字段：报告对应问题码与字段。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("nullFields")
    void test_invalidParameter_nullField(String scenario, Graph graph, String code, String field) {
        Checked checked = validator.check(save(graph));

        assertFalse(checked.validation().valid());
        assertEquals(List.of(field), checked.validation().issues().stream()
            .filter(issue -> issue.code().equals(code)).map(Issue::field).toList());
    }

    static Stream<Arguments> nullFields() {
        Condition nullGroup = new Condition(1, new Group(null, List.of(matched(), matched())));
        Condition nullOperator = new Condition(1, new Predicate("NODE_RESULT", "matched", null,
            List.of(new BooleanLiteral(true))));
        return Stream.of(
            Arguments.of("节点类型为空", graph(new Node("rs", null, "Rule", 0, 0, 1L, null, null, null), List.of()),
                "INVALID_NODE_TYPE", "graph.nodes[1].type"),
            Arguments.of("连线类型为空", graph(ruleSet(), List.of(edge("e_rs", null, 0, null))),
                "INVALID_EDGE_CONFIG", "graph.edges[1]"),
            Arguments.of("节点键为空", graph(new Node(null, RULE_SET, "Rule", 0, 0, 1L, null, null, null), List.of()),
                "INVALID_NODE_KEY", "graph.nodes[1].nodeKey"),
            Arguments.of("条件分组运算符为空", graph(ruleSet(), conditional(nullGroup)),
                "INVALID_CONDITION", "graph.edges[1].condition"),
            Arguments.of("条件运算符为空", graph(ruleSet(), conditional(nullOperator)),
                "INVALID_CONDITION", "graph.edges[1].condition"));
    }

    /** 开始 → 给定节点 → 放行；附加连线来自给定节点。 */
    private static Graph graph(Node middle, List<Edge> middleEdges) {
        List<Node> nodes = List.of(new Node("start", START, "Start", 0, 0, null, null, null, null), middle,
            new Node("allow", END, "Allow", 0, 0, null, DecisionValue.ALLOW.getCode(), null, null,
                Map.of(PaymentAttemptRiskInput.AUTHENTICATION_REQUIREMENT,
                    AuthenticationRequirement.NOT_REQUIRED.getCode())));
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge("e_start", "start", "rs", UNCONDITIONAL, 0, "Start", null));
        edges.addAll(middleEdges);
        return new Graph(1, nodes, edges);
    }

    private static Node ruleSet() {
        return new Node("rs", RULE_SET, "Rule", 0, 0, 1L, null, null, null);
    }

    private static Edge edge(String key, String type, int priority, Condition condition) {
        return new Edge(key, "rs", "allow", type, priority, "Edge", condition);
    }

    private static List<Edge> conditional(Condition condition) {
        return List.of(edge("e_rs", CONDITIONAL, 1, condition), edge("e_else", ELSE, 0, null));
    }

    private static Predicate matched() {
        return new Predicate("NODE_RESULT", "matched", "EQ", List.of(new BooleanLiteral(true)));
    }

    private static Save save(Graph graph) {
        return new Save(PaymentAttemptRiskInput.POINT_CODE, "T_FLOW", "Flow", null, null, "change", graph);
    }
}

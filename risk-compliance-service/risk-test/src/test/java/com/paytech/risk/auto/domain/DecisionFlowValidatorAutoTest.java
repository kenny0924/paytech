package com.paytech.risk.auto.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry;
import com.paytech.risk.decision.domain.flow.DecisionFlow.*;
import com.paytech.risk.decision.domain.flow.DecisionFlowJsonCodec;
import com.paytech.risk.decision.domain.flow.DecisionFlowValidator;
import com.paytech.risk.decision.domain.flow.DecisionFlowValidatorImpl;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.BooleanLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.StringLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidatorImpl;
import com.paytech.risk.decision.domain.variable.VariableOperand;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NullLiteral;
import com.paytech.risk.decision.domain.enums.*;

/** 纯图算法和存储格式的独立行为证据，不替代真实 HTTP/数据库回归。 */
class DecisionFlowValidatorAutoTest {
    private final DecisionFlowJsonCodec codec = new DecisionFlowJsonCodec();
    private final DecisionFlowValidator validator = new DecisionFlowValidatorImpl(new RuleAstValidatorImpl(
        CheckoutPrepareAuthoringDescriptor.v1(), DecisionActionRegistry.checkoutPrepareV1()), codec);

    @Test
    void test_branchingAndEarlyTerminationRemainDeclarative() {
        Checked checked = validator.check(save(graph()));
        assertTrue(checked.validation().valid(), checked.validation().toString());
        assertEquals(-120, checked.normalized().graph().nodes().stream().filter(n -> n.nodeKey().equals("start")).findFirst().orElseThrow().x());
        Condition condition = checked.normalized().graph().edges().stream().filter(e -> e.condition() != null).findFirst().orElseThrow().condition();
        assertEquals(condition, codec.readCondition(codec.conditionJson(condition)));
    }

    @Test
    void test_arrayOrderDoesNotChangeCommandIdentityButBranchPriorityDoes() {
        Graph original = graph();
        var nodes = new ArrayList<>(original.nodes()); Collections.reverse(nodes);
        var edges = new ArrayList<>(original.edges()); Collections.reverse(edges);
        assertEquals(codec.saveHash(validator.check(save(original)).normalized()).sha256LowerHex(),
            codec.saveHash(validator.check(save(new Graph(1, nodes, edges))).normalized()).sha256LowerHex());
        assertNotEquals(codec.saveHash(save(original)).sha256LowerHex(), codec.deleteHash(1, 1).sha256LowerHex());
    }

    @Test
    void test_missingFallbackAndDuplicatePriorityAreLocated() {
        var edges = new ArrayList<>(graph().edges()); edges.removeIf(e -> e.type().equals("ELSE"));
        assertIssue(new Graph(1, graph().nodes(), edges), "ELSE_REQUIRED", "rules");
        edges = new ArrayList<>(graph().edges());
        edges.add(new Edge("duplicate", "rules", "allow", "CONDITIONAL", 1, "另一个条件", matched()));
        assertIssue(new Graph(1, graph().nodes(), edges), "DUPLICATE_PRIORITY", "rules");
    }

    @Test
    void test_cyclesAndDisconnectedNodesNeverPass() {
        var edges = new ArrayList<>(graph().edges());
        edges.set(1, new Edge("hit", "rules", "rules", "CONDITIONAL", 1, "自环", matched()));
        assertIssue(new Graph(1, graph().nodes(), edges), "CYCLE", "rules");
        var nodes = new ArrayList<>(graph().nodes()); nodes.add(end("orphan", "ALLOW"));
        assertIssue(new Graph(1, nodes, graph().edges()), "UNREACHABLE_NODE", "orphan");
    }

    @Test
    void test_incompleteEndDecisionIsAReport() {
        var nodes = new ArrayList<>(graph().nodes()); nodes.set(2, end("review", null));
        assertIssue(new Graph(1, nodes, graph().edges()), "END_DECISION_REQUIRED", "review");
    }

    @Test
    void test_conditionTypesAndForbiddenSourcesCannotBeCoerced() {
        for (Condition invalid : List.of(
            new Condition(1, new Predicate("NODE_RESULT", "matched", "EQ", List.of(new StringLiteral("true")))),
            new Condition(1, new Predicate("NODE_RESULT", "otherNode.matched", "EQ", List.of(new BooleanLiteral(true)))),
            new Condition(1, new Predicate("SCRIPT", "matched", "EQ", List.of(new BooleanLiteral(true)))),
            new Condition(1, new Group("NOT", List.of(matched().root(), matched().root()))))) {
            var edges = new ArrayList<>(graph().edges());
            edges.set(1, new Edge("hit", "rules", "review", "CONDITIONAL", 1, "命中", invalid));
            assertIssue(new Graph(1, graph().nodes(), edges), "INVALID_CONDITION", null);
        }
    }

    @Test
    void test_conditionDepthIsBoundedAndCorruptStorageIsNotAnEmptyCondition() {
        Expression root = matched().root();
        for (int i = 0; i < 8; i++) root = new Group("NOT", List.of(root));
        var edges = new ArrayList<>(graph().edges());
        edges.set(1, new Edge("hit", "rules", "review", "CONDITIONAL", 1, "命中", new Condition(1, root)));
        assertIssue(new Graph(1, graph().nodes(), edges), "INVALID_CONDITION", null);
        for (String json : List.of("{}", "null", "{\"schemaVersion\":1,\"root\":null}",
            "{\"schemaVersion\":1,\"schemaVersion\":1,\"root\":{}}"))
            assertThrows(ErrorException.class, () -> codec.readCondition(json));
    }

    @Test
    void test_variableDecisionCanContinueToAnotherRuleSetAndRoundTripsWithoutRuntimeValues() {
        Condition condition = new Condition(2, new Predicate(null, null, "EQ", null, List.of(
            new VariableOperand(OperandRole.LEFT, 1, OperandSource.VARIABLE, "OUTPUT_DECISION", null,
                DecisionValueType.STRING, NullLiteral.INSTANCE, null, null, null),
            new VariableOperand(OperandRole.RIGHT, 1, OperandSource.LITERAL, null, null,
                DecisionValueType.STRING, new StringLiteral("DENY"), null, null, null))));
        var nodes = new ArrayList<>(graph().nodes());
        nodes.set(2, new Node("review", "RULE_SET", "后续规则", 400, 100, 456L, null, null, null));
        var edges = new ArrayList<>(graph().edges());
        edges.set(1, new Edge("hit", "rules", "review", "CONDITIONAL", 1, "拒绝后继续", condition));
        edges.add(new Edge("continue", "review", "allow", "UNCONDITIONAL", 0, "继续", null));
        Checked checked = validator.check(save(new Graph(1, nodes, edges)));
        assertTrue(checked.validation().valid(), checked.validation().toString());
        String json = codec.conditionJson(condition);
        assertTrue(json.contains("\"schemaVersion\":2"));
        assertTrue(json.contains("\"variableCode\":\"OUTPUT_DECISION\""));
        assertFalse(json.contains("\"values\""));
        assertFalse(json.contains("currentValue"));
        assertEquals(condition, codec.readCondition(json));
    }

    @Test
    void test_corruptStoredOperatorAndEmptyGroupCannotHideReferences() {
        assertThrows(ErrorException.class, () -> codec.readCondition(
            "{\"schemaVersion\":2,\"root\":{\"kind\":\"GROUP\",\"operator\":\"OR\",\"children\":[]}}"));
        assertThrows(ErrorException.class, () -> codec.readCondition(codec.conditionJson(matched()).replace("\"EQ\"", "\"OTHER\"")));
    }

    private void assertIssue(Graph graph, String code, String nodeKey) {
        Validation result = validator.check(save(graph)).validation();
        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals(code)
            && (nodeKey == null || nodeKey.equals(i.nodeKey()))), result.toString());
    }

    private static Save save(Graph graph) {
        return new Save("CHECKOUT_PREPARE", "FLOW_TEST", "结账流程", null, null, "配置初版", graph);
    }

    private static Condition matched() {
        return new Condition(1, new Predicate("NODE_RESULT", "matched", "EQ", List.of(new BooleanLiteral(true))));
    }

    private static Node end(String key, String decision) {
        return new Node(key, "END", "结束", 400, 100, null, decision, null, null);
    }

    private static Graph graph() {
        return new Graph(1, List.of(new Node("start", "START", "开始", -120, 0, null, null, null, null),
            new Node("rules", "RULE_SET", "异常规则", 100, 0, 123L, null, null, null),
            end("review", "REVIEW"), end("allow", "ALLOW")),
            List.of(new Edge("initial", "start", "rules", "UNCONDITIONAL", 0, "开始", null),
                new Edge("hit", "rules", "review", "CONDITIONAL", 1, "命中", matched()),
                new Edge("fallback", "rules", "allow", "ELSE", 0, "其他情况", null)));
    }
}

package com.paytech.platform.support.workflow.auto.domain;

import static com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder.clause;
import static com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder.clauseIn;
import static com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder.when;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.workflow.core.enums.ConditionOperator;
import com.paytech.platform.support.workflow.domain.graph.ConditionClause;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;
import com.paytech.platform.support.workflow.domain.variable.WorkflowVariables;
import com.paytech.platform.support.workflow.domainservice.process.WorkflowConditionEvaluator;
import com.paytech.platform.support.workflow.test.builder.WorkflowFixtures;
import com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 结构化条件求值（规则 B4）：各比较符、空值语义、条件组与排他网关选边。
 *
 * @see WorkflowConditionEvaluator#matches(ConditionClause, WorkflowVariables)
 * @see WorkflowConditionEvaluator#selectEdge(WorkflowNode, WorkflowVariables)
 */
class WorkflowConditionEvaluatorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final WorkflowConditionEvaluator evaluator = new WorkflowConditionEvaluator();

    private final WorkflowVariables variables = variables("""
        {"channelCode":"GEIDEA_SA","note":"launch Geidea in KSA","paymentMethod":"APPLE_PAY","amount":"1500.50",
         "urgent":true,"launchDate":"2026-11-01","contractFiles":["730000000000000001"]}
        """);

    @Test
    void test_equalityOperators() {
        assertMatch(true, clause("start.channelCode", ConditionOperator.EQ, "GEIDEA_SA"));
        assertMatch(false, clause("start.channelCode", ConditionOperator.EQ, "geidea_sa"));
        assertMatch(true, clause("start.paymentMethod", ConditionOperator.NE, "CARD"));
        assertMatch(true, clause("start.amount", ConditionOperator.EQ, "1500.5"));
        assertMatch(true, clause("start.urgent", ConditionOperator.EQ, "true"));
        assertMatch(false, clause("start.urgent", ConditionOperator.NE, "true"));
        assertMatch(true, clause("start.launchDate", ConditionOperator.EQ, "2026-11-01"));
    }

    @Test
    void test_listAndTextOperators() {
        assertMatch(true, clauseIn("start.paymentMethod", ConditionOperator.IN, "CARD", "APPLE_PAY"));
        assertMatch(false, clauseIn("start.paymentMethod", ConditionOperator.IN, "CARD"));
        assertMatch(true, clauseIn("start.amount", ConditionOperator.IN, "10", "1500.500"));
        assertMatch(true, clause("start.note", ConditionOperator.CONTAINS, "Geidea"));
        assertMatch(false, clause("start.note", ConditionOperator.CONTAINS, "geidea"));
    }

    @Test
    void test_orderingOperators() {
        assertMatch(true, clause("start.amount", ConditionOperator.GT, "1500"));
        assertMatch(false, clause("start.amount", ConditionOperator.GT, "1500.50"));
        assertMatch(true, clause("start.amount", ConditionOperator.GTE, "1500.50"));
        assertMatch(true, clause("start.amount", ConditionOperator.LT, "2000"));
        assertMatch(true, clause("start.amount", ConditionOperator.LTE, "1500.5"));
        assertMatch(false, clause("start.amount", ConditionOperator.LTE, "-1"));
        assertMatch(true, clause("start.launchDate", ConditionOperator.GT, "2026-10-31"));
        assertMatch(true, clause("start.launchDate", ConditionOperator.LTE, "2026-11-01"));
        assertMatch(false, clause("start.launchDate", ConditionOperator.LT, "2026-11-01"));
    }

    @Test
    void test_emptyValues() {
        WorkflowVariables empty = variables("""
            {"channelCode":"","note":null,"contractFiles":[]}
            """);
        for (String name : List.of("start.channelCode", "start.note", "start.contractFiles", "start.amount")) {
            Assertions.assertTrue(evaluator.matches(clause(name, ConditionOperator.IS_EMPTY, null), empty), name);
            Assertions.assertFalse(evaluator.matches(clause(name, ConditionOperator.IS_NOT_EMPTY, null), empty), name);
        }
        Assertions.assertFalse(evaluator.matches(clause("start.channelCode", ConditionOperator.NE, "X"), empty));
        Assertions.assertFalse(evaluator.matches(clause("start.amount", ConditionOperator.LT, "10"), empty));
        Assertions.assertTrue(evaluator.matches(clause("start.undeclared", ConditionOperator.IS_EMPTY, null), empty));
        assertMatch(true, clause("start.contractFiles", ConditionOperator.IS_NOT_EMPTY, null));
    }

    @Test
    void test_selectEdge() {
        WorkflowNode gateway = WorkflowGraphBuilder.graph()
            .exclusive("gate", "e_default",
                when("e_card", clause("start.paymentMethod", ConditionOperator.EQ, "CARD")),
                when("e_bigApple", clause("start.paymentMethod", ConditionOperator.EQ, "APPLE_PAY"),
                    clause("start.amount", ConditionOperator.GT, "1000")),
                when("e_apple", clause("start.paymentMethod", ConditionOperator.EQ, "APPLE_PAY")))
            .build().node("gate").orElseThrow();
        Assertions.assertEquals("e_bigApple", evaluator.selectEdge(gateway, variables));
        Assertions.assertEquals("e_apple", evaluator.selectEdge(gateway, variables("""
            {"paymentMethod":"APPLE_PAY","amount":"10"}
            """)));
        Assertions.assertEquals("e_default", evaluator.selectEdge(gateway, variables("""
            {"paymentMethod":"OTHER"}
            """)));
        Assertions.assertFalse(evaluator.matchesAll(List.of(), variables));
    }

    @Test
    void test_inconsistentDataFailsClosed() {
        WorkflowVariables malformed = variables("""
            {"amount":"1e3","launchDate":"2026-02-30","urgent":"true"}
            """);
        Assertions.assertThrows(ErrorException.class,
            () -> evaluator.matches(clause("start.amount", ConditionOperator.GT, "1"), malformed));
        Assertions.assertThrows(ErrorException.class,
            () -> evaluator.matches(clause("start.launchDate", ConditionOperator.GT, "2026-01-01"), malformed));
        Assertions.assertThrows(ErrorException.class,
            () -> evaluator.matches(clause("start.urgent", ConditionOperator.EQ, "true"), malformed));
        Assertions.assertThrows(ErrorException.class,
            () -> evaluator.matches(clause("start.contractFiles", ConditionOperator.EQ, "x"), variables));
    }

    /**
     * 断言子句在基线变量下的结果。
     *
     * @param expected 期望结果
     * @param clause 子句
     */
    private void assertMatch(boolean expected, ConditionClause clause) {
        Assertions.assertEquals(expected, evaluator.matches(clause, variables), clause::toString);
    }

    /**
     * 按启动表单基线声明组装 start.* 变量。
     *
     * @param startValues start_values_json
     * @return 实例变量
     */
    private static WorkflowVariables variables(String startValues) {
        JsonNode values = JSON.readTree(startValues);
        return WorkflowVariables.builder()
            .add(VariableDeclaration.ofForm(VariableDeclaration.START_PREFIX, WorkflowFixtures.startForm()), values)
            .build();
    }
}

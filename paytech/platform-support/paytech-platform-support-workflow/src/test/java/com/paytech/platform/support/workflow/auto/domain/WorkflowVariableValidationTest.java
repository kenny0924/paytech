package com.paytech.platform.support.workflow.auto.domain;

import static com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder.clause;
import static com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder.clauseIn;
import static com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.workflow.core.enums.ApprovalMode;
import com.paytech.platform.support.workflow.core.enums.ConditionOperator;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.graph.ConditionClause;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;
import com.paytech.platform.support.workflow.domainservice.validator.WorkflowGraphValidator;
import com.paytech.platform.support.workflow.test.builder.PublicationInputBuilder;
import com.paytech.platform.support.workflow.test.builder.WorkflowFixtures;
import com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder;
import com.paytech.platform.support.workflow.test.checker.ProblemChecker;

/**
 * 发布校验 B1 变量：条件与输入映射引用的变量来自启动表单或必经的上游动作输出，类型与比较符匹配，
 * 并行分支之间互不可见、汇合后可用（技术方案 3.4 第 7 项）。
 *
 * @see WorkflowGraphValidator#validate(com.paytech.platform.support.workflow.domain.validation.WorkflowPublicationInput)
 */
class WorkflowVariableValidationTest {

    private final WorkflowGraphValidator validator = new WorkflowGraphValidator();

    @Test
    void test_normalFlow() {
        ProblemChecker.of(validate(gateAfterForm(clause("form.riskLevel", ConditionOperator.EQ, "HIGH")))).none();
        ProblemChecker.of(validate(gateAfterForm(clause("form.amount", ConditionOperator.GTE, "100.5"),
            clause("start.launchDate", ConditionOperator.LT, "2026-12-31"),
            clause("start.urgent", ConditionOperator.NE, "false"),
            clause("start.note", ConditionOperator.CONTAINS, "Geidea"),
            clause("start.contractFiles", ConditionOperator.IS_NOT_EMPTY, null),
            clauseIn("start.paymentMethod", ConditionOperator.IN, "CARD", "APPLE_PAY")))).none();
    }

    @Test
    void test_unknownOrDownstreamVariable() {
        ProblemChecker.of(validate(gateAfterForm(clause("start.unknown", ConditionOperator.EQ, "x"))))
            .edge(WorkflowProblemCode.VARIABLE_UNAVAILABLE, "g_high");
        WorkflowGraph downstream = WorkflowGraphBuilder.graph().start("start")
            .exclusive("gate", "g_low", when("g_high", clause("later.riskLevel", ConditionOperator.EQ, "HIGH")))
            .task("later", task -> task.action("later", WorkflowFixtures.TPL_FORM, WorkflowFixtures.EXECUTOR_A))
            .task("high", this::approval).end("end")
            .edge("s", "start", "gate").edge("g_high", "gate", "high").edge("g_low", "gate", "later")
            .chain("high", "later").chain("later", "end")
            .build();
        ProblemChecker.of(validate(downstream)).exactly(WorkflowProblemCode.VARIABLE_UNAVAILABLE);
    }

    @Test
    void test_exclusiveBranchOutputUnavailableAfterMerge() {
        WorkflowGraph graph = WorkflowGraphBuilder.graph().start("start")
            .exclusive("gate", "g_skip", when("g_form", clause("start.urgent", ConditionOperator.EQ, "true")))
            .task("fill", task -> task.action("fill", WorkflowFixtures.TPL_FORM, WorkflowFixtures.EXECUTOR_A))
            .exclusive("inside", "i_b", when("i_a", clause("fill.riskLevel", ConditionOperator.EQ, "HIGH")))
            .task("a", this::approval).task("b", this::approval)
            .task("merge", this::approval)
            .exclusive("after", "after_end", when("after_x", clause("fill.riskLevel", ConditionOperator.EQ, "LOW")))
            .task("x", this::approval).end("end")
            .edge("s", "start", "gate").edge("g_form", "gate", "fill").edge("g_skip", "gate", "merge")
            .chain("fill", "inside").edge("i_a", "inside", "a").edge("i_b", "inside", "b")
            .chain("a", "merge").chain("b", "merge").chain("merge", "after")
            .edge("after_x", "after", "x").edge("after_end", "after", "end").chain("x", "end")
            .build();
        ProblemChecker.of(validate(graph)).exactly(WorkflowProblemCode.VARIABLE_UNAVAILABLE)
            .edge(WorkflowProblemCode.VARIABLE_UNAVAILABLE, "after_x");
    }

    @Test
    void test_parallelSiblingOutputs() {
        WorkflowGraph graph = WorkflowGraphBuilder.graph().start("start").split("fork").join("join")
            .task("a1", task -> task.action("riskForm", WorkflowFixtures.TPL_FORM, WorkflowFixtures.EXECUTOR_A))
            .task("b1", task -> task.actionWithVariableInput("page", WorkflowFixtures.TPL_PAGE, "channelCode",
                "riskForm.riskLevel"))
            .exclusive("after", "after_end", when("after_x", clause("riskForm.riskLevel", ConditionOperator.EQ, "HIGH")))
            .task("x", this::approval).end("end")
            .chain("start", "fork").chain("fork", "a1", "join").chain("fork", "b1", "join").chain("join", "after")
            .edge("after_x", "after", "x").edge("after_end", "after", "end").chain("x", "end")
            .build();
        ProblemChecker.of(validate(graph)).exactly(WorkflowProblemCode.VARIABLE_UNAVAILABLE)
            .item(WorkflowProblemCode.VARIABLE_UNAVAILABLE, "page");
    }

    @Test
    void test_operatorTypeMismatch() {
        ProblemChecker.of(validate(gateAfterForm(clause("start.channelCode", ConditionOperator.GT, "A"))))
            .exactly(WorkflowProblemCode.OPERATOR_TYPE_MISMATCH);
        ProblemChecker.of(validate(gateAfterForm(clause("start.amount", ConditionOperator.CONTAINS, "1"))))
            .exactly(WorkflowProblemCode.OPERATOR_TYPE_MISMATCH);
        ProblemChecker.of(validate(gateAfterForm(clause("start.contractFiles", ConditionOperator.EQ, "1"))))
            .exactly(WorkflowProblemCode.OPERATOR_TYPE_MISMATCH);
        ProblemChecker.of(validate(gateAfterForm(clauseIn("start.urgent", ConditionOperator.IN, "true"))))
            .exactly(WorkflowProblemCode.OPERATOR_TYPE_MISMATCH);
    }

    @Test
    void test_conditionValueInvalid() {
        for (ConditionClause invalid : List.of(
            clause("start.amount", ConditionOperator.EQ, "1e3"),
            clause("start.launchDate", ConditionOperator.GT, "2026-13-01"),
            clause("start.urgent", ConditionOperator.EQ, "yes"),
            clause("start.paymentMethod", ConditionOperator.EQ, "PAYPAL"),
            clause("start.paymentMethod", ConditionOperator.IN, "CARD"),
            clauseIn("start.paymentMethod", ConditionOperator.IN),
            clause("start.channelCode", ConditionOperator.IS_EMPTY, "x"),
            clause("start.channelCode", ConditionOperator.EQ, null))) {
            ProblemChecker.of(validate(gateAfterForm(invalid))).exactly(WorkflowProblemCode.CONDITION_VALUE_INVALID);
        }
    }

    @Test
    void test_inputVariableUnavailable() {
        WorkflowGraph graph = WorkflowGraphBuilder.graph().start("start")
            .task("page", task -> task.actionWithVariableInput("page", WorkflowFixtures.TPL_PAGE, "channelCode",
                "start.missing"))
            .end("end").chain("start", "page", "end").build();
        ProblemChecker.of(validate(graph)).exactly(WorkflowProblemCode.VARIABLE_UNAVAILABLE)
            .item(WorkflowProblemCode.VARIABLE_UNAVAILABLE, "page");
    }

    @Test
    void test_missingTemplateDoesNotCascade() {
        WorkflowGraph graph = gateAfterForm(clause("form.riskLevel", ConditionOperator.EQ, "HIGH"));
        ProblemChecker.of(validator.validate(PublicationInputBuilder.of(graph).withoutTemplate(WorkflowFixtures.TPL_FORM)
            .build())).exactly(WorkflowProblemCode.TEMPLATE_NOT_FOUND);
    }

    /**
     * start → fill（表单动作 form）→ gate（g_high：给定子句，默认 g_low）→ end。
     *
     * @param clauses 条件子句
     * @return 流程图
     */
    private WorkflowGraph gateAfterForm(ConditionClause... clauses) {
        return WorkflowGraphBuilder.graph().start("start")
            .task("fill", task -> task.action("form", WorkflowFixtures.TPL_FORM, WorkflowFixtures.EXECUTOR_A))
            .exclusive("gate", "g_low", when("g_high", clauses))
            .task("high", this::approval).end("end")
            .chain("start", "fill", "gate").edge("g_high", "gate", "high").edge("g_low", "gate", "end")
            .chain("high", "end")
            .build();
    }

    /**
     * 只有审批人甲的任务节点。
     *
     * @param task 节点配置
     */
    private void approval(WorkflowGraphBuilder.TaskSpec task) {
        task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A);
    }

    /**
     * 以默认输入校验。
     *
     * @param graph 流程图
     * @return 问题
     */
    private List<WorkflowGraphProblem> validate(WorkflowGraph graph) {
        return validator.validate(PublicationInputBuilder.of(graph).build());
    }
}

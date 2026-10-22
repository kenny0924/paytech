package com.paytech.platform.support.workflow.auto.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.workflow.core.enums.ApprovalMode;
import com.paytech.platform.support.workflow.core.enums.ConditionOperator;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;
import com.paytech.platform.support.workflow.domainservice.validator.WorkflowGraphValidator;
import com.paytech.platform.support.workflow.test.builder.PublicationInputBuilder;
import com.paytech.platform.support.workflow.test.builder.WorkflowFixtures;
import com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder;
import com.paytech.platform.support.workflow.test.checker.ProblemChecker;

/**
 * 发布校验 B1 并行块：分叉与汇合成对、分支不跨越、不嵌套、结束节点不在块内（技术方案 3.4 第 4 项）。
 *
 * @see WorkflowGraphValidator#validate(com.paytech.platform.support.workflow.domain.validation.WorkflowPublicationInput)
 */
class WorkflowParallelValidationTest {

    private final WorkflowGraphValidator validator = new WorkflowGraphValidator();

    @Test
    void test_normalFlow() {
        ProblemChecker.of(validate(block().chain("fork", "a1", "a2", "join").chain("fork", "b1", "join").build())).none();
        WorkflowGraph serialBlocks = WorkflowGraphBuilder.graph().start("start").split("fork").join("join")
            .split("fork2").join("join2").end("end")
            .task("a1", this::approval).task("b1", this::approval).task("c1", this::approval).task("d1", this::approval)
            .chain("start", "fork").chain("fork", "a1", "join").chain("fork", "b1", "join").chain("join", "fork2")
            .chain("fork2", "c1", "join2").chain("fork2", "d1", "join2").chain("join2", "end")
            .build();
        ProblemChecker.of(validate(serialBlocks)).none();
    }

    @Test
    void test_unpaired() {
        WorkflowGraph twoJoins = block().join("join2").task("tail", this::approval)
            .chain("fork", "a1", "join").chain("fork", "b1", "join2").chain("join2", "tail", "end")
            .build();
        ProblemChecker.of(validate(twoJoins)).node(WorkflowProblemCode.PARALLEL_UNPAIRED, "fork")
            .node(WorkflowProblemCode.PARALLEL_UNPAIRED, "join2");
    }

    @Test
    void test_crossBranch() {
        WorkflowGraph graph = block().task("b2", this::approval)
            .chain("fork", "a1", "join").chain("fork", "b1", "b2", "join").edge("cross", "a1", "b2")
            .build();
        ProblemChecker.of(validate(graph)).edge(WorkflowProblemCode.PARALLEL_BRANCH_CROSS, "cross");
        WorkflowGraph intoJoinFromOutside = WorkflowGraphBuilder.graph().start("start")
            .task("before", this::approval).split("fork").task("a1", this::approval).task("b1", this::approval)
            .join("join").task("after", this::approval).end("end")
            .chain("start", "before", "fork").chain("fork", "a1", "join").chain("fork", "b1", "join")
            .chain("join", "after", "end").edge("shortcut", "before", "join")
            .build();
        ProblemChecker.of(validate(intoJoinFromOutside)).node(WorkflowProblemCode.TASK_OUTGOING, "before")
            .edge(WorkflowProblemCode.PARALLEL_BRANCH_CROSS, "shortcut");
    }

    @Test
    void test_nestedParallel() {
        WorkflowGraph graph = block().split("inner").join("innerJoin").task("x", this::approval)
            .task("y", this::approval)
            .chain("fork", "a1", "join").chain("fork", "inner").chain("inner", "x", "innerJoin")
            .chain("inner", "y", "innerJoin").chain("innerJoin", "join")
            .build();
        ProblemChecker.of(validate(graph)).node(WorkflowProblemCode.PARALLEL_NESTED, "inner")
            .without(WorkflowProblemCode.GRAPH_CYCLE);
    }

    @Test
    void test_endInsideBlock() {
        WorkflowGraph withGate = WorkflowGraphBuilder.graph().start("start").split("fork").join("join").end("end")
            .end("earlyEnd").task("a1", this::approval).task("b1", this::approval).task("b2", this::approval)
            .exclusive("gate", "g_on", WorkflowGraphBuilder.when("g_stop",
                WorkflowGraphBuilder.clause("start.urgent", ConditionOperator.EQ, "true")))
            .chain("start", "fork").chain("fork", "a1", "join").chain("fork", "b1", "gate")
            .edge("g_on", "gate", "b2").edge("g_stop", "gate", "earlyEnd").chain("b2", "join").chain("join", "end")
            .build();
        ProblemChecker.of(validate(withGate)).node(WorkflowProblemCode.PARALLEL_END_INSIDE, "earlyEnd");
    }

    @Test
    void test_emptyBranchAndDoubleJoinEntry() {
        WorkflowGraph empty = block().chain("fork", "a1", "join").edge("direct", "fork", "join").build();
        ProblemChecker.of(validate(empty)).edge(WorkflowProblemCode.PARALLEL_BRANCH_EMPTY, "direct");
        WorkflowGraph doubleEntry = WorkflowGraphBuilder.graph().start("start").split("fork").join("join").end("end")
            .task("a1", this::approval).task("b1", this::approval).task("b2", this::approval)
            .exclusive("gate", "g2", WorkflowGraphBuilder.when("g1",
                WorkflowGraphBuilder.clause("start.urgent", ConditionOperator.EQ, "true")))
            .chain("start", "fork").chain("fork", "a1", "join").chain("fork", "gate")
            .edge("g1", "gate", "b1").edge("g2", "gate", "b2").chain("b1", "join").chain("b2", "join")
            .chain("join", "end")
            .build();
        ProblemChecker.of(validate(doubleEntry)).edge(WorkflowProblemCode.PARALLEL_BRANCH_JOIN_ENTRY, "b2_join");
    }

    /**
     * start → fork，join → end，分支由各用例自行连线；预置 a1、b1 两个审批节点。
     *
     * @return 构造器
     */
    private WorkflowGraphBuilder block() {
        return WorkflowGraphBuilder.graph().start("start").split("fork").join("join").end("end")
            .task("a1", this::approval).task("a2", this::approval).task("b1", this::approval)
            .chain("start", "fork").chain("join", "end");
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

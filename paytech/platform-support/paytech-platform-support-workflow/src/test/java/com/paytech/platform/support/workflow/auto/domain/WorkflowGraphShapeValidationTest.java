package com.paytech.platform.support.workflow.auto.domain;

import static com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder.clause;
import static com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.workflow.core.enums.ApprovalMode;
import com.paytech.platform.support.workflow.core.enums.ConditionOperator;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.graph.NodeApproval;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;
import com.paytech.platform.support.workflow.domainservice.validator.WorkflowGraphValidator;
import com.paytech.platform.support.workflow.test.builder.PublicationInputBuilder;
import com.paytech.platform.support.workflow.test.builder.SampleGraphs;
import com.paytech.platform.support.workflow.test.builder.WorkflowFixtures;
import com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder;
import com.paytech.platform.support.workflow.test.checker.ProblemChecker;

/**
 * 发布校验 B1：上限、key、起止节点、出入边数量、配置归属、排他网关结构、回环与可达性（技术方案 3.4 第 1、2、3、8 项）。
 *
 * @see WorkflowGraphValidator#validate(com.paytech.platform.support.workflow.domain.validation.WorkflowPublicationInput)
 */
class WorkflowGraphShapeValidationTest {

    private final WorkflowGraphValidator validator = new WorkflowGraphValidator();

    @Test
    void test_normalFlow() {
        ProblemChecker.of(validate(SampleGraphs.newChannelLaunch().build())).none();
        ProblemChecker.of(validate(WorkflowGraphBuilder.template().build())).none();
    }

    @Test
    void test_limits() {
        List<WorkflowGraphProblem> tooLarge = validator.validate(PublicationInputBuilder
            .of(WorkflowGraphBuilder.template().build()).graphJsonBytes(48 * 1024 + 1).build());
        ProblemChecker.of(tooLarge).exactly(WorkflowProblemCode.GRAPH_TOO_LARGE);
        ProblemChecker.of(validator.validate(PublicationInputBuilder.of(WorkflowGraphBuilder.template().build())
            .graphJsonBytes(48 * 1024).build())).none();
        ProblemChecker.of(validator.validate(PublicationInputBuilder.of(WorkflowGraphBuilder.template().build())
            .startFormJsonBytes(12 * 1024 + 1).build())).exactly(WorkflowProblemCode.START_FORM_TOO_LARGE);
        ProblemChecker.of(validator.validate(PublicationInputBuilder.of(WorkflowGraphBuilder.template().build())
            .startFormJsonBytes(12 * 1024).build())).none();

        ProblemChecker.of(validate(chainOfTasks(98))).without(WorkflowProblemCode.TOO_MANY_NODES);
        ProblemChecker.of(validate(chainOfTasks(99))).exactly(WorkflowProblemCode.TOO_MANY_NODES);
    }

    @Test
    void test_invalidKeys() {
        WorkflowGraph graph = WorkflowGraphBuilder.template()
            .task("approve", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .task("9bad", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .edge("approve_end", "approve", "end")
            .edge("bad key", "9bad", "end")
            .edge("dangling", "approve", "missing")
            .build();
        ProblemChecker.of(validate(graph))
            .node(WorkflowProblemCode.NODE_KEY_DUPLICATE, "approve")
            .node(WorkflowProblemCode.NODE_KEY_INVALID, "9bad")
            .edge(WorkflowProblemCode.EDGE_KEY_DUPLICATE, "approve_end")
            .edge(WorkflowProblemCode.EDGE_KEY_INVALID, "bad key")
            .edge(WorkflowProblemCode.EDGE_ENDPOINT_UNKNOWN, "dangling");
    }

    @Test
    void test_duplicateEdgeAndMissingName() {
        WorkflowGraph graph = WorkflowGraphBuilder.template().edge("again", "approve", "end")
            .node(new WorkflowNode("nameless", WorkflowNodeType.TASK, " ", null,
                new NodeApproval(ApprovalMode.ANY, List.of(WorkflowFixtures.APPROVER_A)), List.of(), List.of(), null,
                List.of()))
            .build();
        ProblemChecker.of(validate(graph))
            .edge(WorkflowProblemCode.EDGE_DUPLICATE, "again")
            .node(WorkflowProblemCode.NODE_NAME_MISSING, "nameless");
    }

    @Test
    void test_startAndEnd() {
        ProblemChecker.of(validate(WorkflowGraphBuilder.graph()
                .task("t", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A)).build()))
            .node(WorkflowProblemCode.START_COUNT, null)
            .node(WorkflowProblemCode.END_MISSING, null);
        WorkflowGraph twoStarts = WorkflowGraphBuilder.template().start("start2").edge("s2", "start2", "approve").build();
        ProblemChecker.of(validate(twoStarts)).node(WorkflowProblemCode.START_COUNT, null);
        WorkflowGraph wiredWrong = WorkflowGraphBuilder.template()
            .task("other", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .edge("into_start", "other", "start").edge("start_other", "start", "other").edge("out_of_end", "end", "other")
            .build();
        ProblemChecker.of(validate(wiredWrong))
            .node(WorkflowProblemCode.START_HAS_INCOMING, "start")
            .node(WorkflowProblemCode.START_OUTGOING, "start")
            .node(WorkflowProblemCode.END_HAS_OUTGOING, "end");
    }

    @Test
    void test_unreachableNodes() {
        WorkflowGraph graph = WorkflowGraphBuilder.template()
            .task("island", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .edge("island_end", "island", "end")
            .task("deadEnd", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .exclusive("gate", "g_end", when("g_dead", clause("start.urgent", ConditionOperator.EQ, "true")))
            .build();
        WorkflowGraph rewired = WorkflowGraphBuilder.graph().start("start")
            .task("approve", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .task("deadEnd", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .exclusive("gate", "g_end", when("g_dead", clause("start.urgent", ConditionOperator.EQ, "true")))
            .end("end").chain("start", "approve", "gate").edge("g_end", "gate", "end").edge("g_dead", "gate", "deadEnd")
            .build();
        ProblemChecker.of(validate(graph)).node(WorkflowProblemCode.NODE_UNREACHABLE, "island");
        ProblemChecker.of(validate(rewired)).node(WorkflowProblemCode.NODE_CANNOT_REACH_END, "deadEnd")
            .node(WorkflowProblemCode.TASK_OUTGOING, "deadEnd");
    }

    @Test
    void test_degreeRules() {
        WorkflowGraph graph = WorkflowGraphBuilder.graph().start("start")
            .task("fanOut", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .exclusive("narrowGate", "n1")
            .split("narrowSplit").join("narrowJoin")
            .end("end").end("end2")
            .chain("start", "fanOut", "narrowGate").edge("fan2", "fanOut", "end2")
            .edge("n1", "narrowGate", "narrowSplit").edge("ns1", "narrowSplit", "narrowJoin")
            .edge("nj1", "narrowJoin", "end").edge("nj2", "narrowJoin", "end2")
            .build();
        ProblemChecker.of(validate(graph))
            .node(WorkflowProblemCode.TASK_OUTGOING, "fanOut")
            .node(WorkflowProblemCode.EXCLUSIVE_OUTGOING, "narrowGate")
            .node(WorkflowProblemCode.PARALLEL_SPLIT_OUTGOING, "narrowSplit")
            .node(WorkflowProblemCode.PARALLEL_JOIN_INCOMING, "narrowJoin")
            .node(WorkflowProblemCode.PARALLEL_JOIN_OUTGOING, "narrowJoin");
    }

    @Test
    void test_configOnWrongNodeType() {
        WorkflowGraph graph = WorkflowGraphBuilder.graph()
            .node(new WorkflowNode("start", WorkflowNodeType.START, null, null,
                new NodeApproval(ApprovalMode.ANY, List.of(WorkflowFixtures.APPROVER_A)), List.of(), List.of(), "x",
                List.of()))
            .task("approve", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .end("end").chain("start", "approve", "end").build();
        ProblemChecker.of(validate(graph)).exactly(WorkflowProblemCode.NODE_CONFIG_NOT_ALLOWED);
    }

    @Test
    void test_exclusiveConditions() {
        WorkflowGraph graph = WorkflowGraphBuilder.graph().start("start")
            .exclusive("gate", "not_mine",
                when("c1", clause("start.urgent", ConditionOperator.EQ, "true")),
                when("c1", clause("start.urgent", ConditionOperator.EQ, "false")),
                when("foreign", clause("start.urgent", ConditionOperator.EQ, "true")),
                when("c2"))
            .task("a", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .task("b", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .task("c", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .end("end")
            .edge("s", "start", "gate").edge("c1", "gate", "a").edge("c2", "gate", "b").edge("c3", "gate", "c")
            .edge("foreign", "a", "end").chain("b", "end").chain("c", "end")
            .build();
        ProblemChecker.of(validate(graph))
            .edge(WorkflowProblemCode.EXCLUSIVE_DEFAULT_INVALID, "not_mine")
            .edge(WorkflowProblemCode.CONDITION_EDGE_DUPLICATE, "c1")
            .edge(WorkflowProblemCode.CONDITION_EDGE_INVALID, "foreign")
            .edge(WorkflowProblemCode.CONDITION_EMPTY, "c2")
            .edge(WorkflowProblemCode.EXCLUSIVE_EDGE_UNUSED, "c3");
        WorkflowGraph noDefault = WorkflowGraphBuilder.graph().start("start")
            .exclusive("gate", null, when("g1", clause("start.urgent", ConditionOperator.EQ, "true")))
            .end("end").edge("s", "start", "gate").edge("g1", "gate", "end").edge("g2", "gate", "end")
            .build();
        ProblemChecker.of(validate(noDefault)).node(WorkflowProblemCode.EXCLUSIVE_DEFAULT_MISSING, "gate");
    }

    @Test
    void test_conditionOnDefaultEdge() {
        WorkflowGraph graph = WorkflowGraphBuilder.graph().start("start")
            .exclusive("gate", "g1", when("g1", clause("start.urgent", ConditionOperator.EQ, "true")))
            .task("a", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .end("end").edge("s", "start", "gate").edge("g1", "gate", "a").edge("g2", "gate", "end").chain("a", "end")
            .build();
        ProblemChecker.of(validate(graph))
            .edge(WorkflowProblemCode.CONDITION_EDGE_DUPLICATE, "g1")
            .edge(WorkflowProblemCode.EXCLUSIVE_EDGE_UNUSED, "g2");
    }

    @Test
    void test_cycleRejected() {
        WorkflowGraph graph = WorkflowGraphBuilder.graph().start("start")
            .task("a", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .exclusive("gate", "back", when("forward", clause("start.urgent", ConditionOperator.EQ, "true")))
            .end("end")
            .chain("start", "a", "gate").edge("forward", "gate", "end").edge("back", "gate", "a")
            .build();
        ProblemChecker.of(validate(graph)).edge(WorkflowProblemCode.GRAPH_CYCLE, "back");
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

    /**
     * start + n 个审批节点 + end 的直线流程。
     *
     * @param tasks 任务节点数
     * @return 流程图
     */
    private WorkflowGraph chainOfTasks(int tasks) {
        WorkflowGraphBuilder builder = WorkflowGraphBuilder.graph().start("start").end("end");
        String previous = "start";
        for (int index = 0; index < tasks; index++) {
            String key = "t" + index;
            builder.task(key, task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A)).chain(previous, key);
            previous = key;
        }
        return builder.chain(previous, "end").build();
    }
}

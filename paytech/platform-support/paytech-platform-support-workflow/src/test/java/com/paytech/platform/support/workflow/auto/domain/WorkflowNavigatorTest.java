package com.paytech.platform.support.workflow.auto.domain;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.core.enums.RejectionKind;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.runtime.NavigationPlan;
import com.paytech.platform.support.workflow.domain.runtime.NodeRunState;
import com.paytech.platform.support.workflow.domain.runtime.RejectOption;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;
import com.paytech.platform.support.workflow.domain.variable.WorkflowVariables;
import com.paytech.platform.support.workflow.domainservice.process.WorkflowNavigator;
import com.paytech.platform.support.workflow.test.builder.SampleGraphs;
import com.paytech.platform.support.workflow.test.builder.WorkflowFixtures;
import com.paytech.platform.support.workflow.test.util.NodeRunLedger;

import tools.jackson.databind.json.JsonMapper;

/**
 * 运行导航（技术方案第 4 节，规则 B3–B5a）：顺序推进、排他选边与默认分支、并行分叉与汇合、END 完成、
 * 退回修改、驳回四种位置与终止。使用“新渠道上线”样例图，见 {@link SampleGraphs#newChannelLaunch()}。
 *
 * @see WorkflowNavigator#start(WorkflowGraph, WorkflowVariables, java.util.function.LongSupplier)
 * @see WorkflowNavigator#completeTask(WorkflowGraph, java.util.Collection, Long, WorkflowVariables,
 *     java.util.function.LongSupplier)
 * @see WorkflowNavigator#returnForRevision(WorkflowGraph, java.util.Collection, Long, java.util.function.LongSupplier)
 * @see WorkflowNavigator#rejectOption(WorkflowGraph, java.util.Collection, Long)
 * @see WorkflowNavigator#reject(WorkflowGraph, java.util.Collection, Long, String, java.util.function.LongSupplier)
 * @see WorkflowNavigator#cancelOpenRuns(WorkflowGraph, java.util.Collection)
 */
class WorkflowNavigatorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final WorkflowNavigator navigator = new WorkflowNavigator();

    private final WorkflowGraph graph = SampleGraphs.newChannelLaunch().build();

    private NodeRunLedger ledger;

    @BeforeEach
    void setUpLedger() {
        ledger = new NodeRunLedger();
    }

    @Test
    void test_sequentialFlow() {
        NavigationPlan started = ledger.apply(navigator.start(graph, card(), ledger::nextId));
        NodeRunState start = ledger.latest("start");
        Assertions.assertEquals(NodeRunStatus.PASSED, start.status());
        Assertions.assertNull(start.previousNodeRunId());
        assertRun("approve", 1, NodeRunStatus.APPROVING, null, start.nodeRunId());
        Assertions.assertEquals(List.of("approve"), started.activatedTasks().stream().map(NodeRunState::nodeKey).toList());
        Assertions.assertFalse(started.instanceCompleted());

        NavigationPlan approved = complete("approve", card());
        Assertions.assertEquals(1, approved.statusChanges().size());
        Assertions.assertEquals(NodeRunStatus.APPROVING, approved.statusChanges().get(0).fromStatus());
        assertRun("commerce", 1, NodeRunStatus.ACTIVE, null, ledger.latest("approve").nodeRunId());
    }

    @Test
    void test_exclusiveDefaultBranchAndParallelSplit() {
        reachCommerce();
        NavigationPlan plan = complete("commerce", card());
        NodeRunState gate = ledger.latest("applePayGate");
        Assertions.assertEquals(NodeRunStatus.PASSED, gate.status());
        Assertions.assertEquals("e_skip", gate.selectedEdgeKey());
        NodeRunState fork = ledger.latest("fork");
        Assertions.assertEquals(gate.nodeRunId(), fork.previousNodeRunId());
        assertRun("risk1", 1, NodeRunStatus.ACTIVE, "b_risk", fork.nodeRunId());
        assertRun("channel1", 1, NodeRunStatus.ACTIVE, "b_channel", fork.nodeRunId());
        Assertions.assertEquals(List.of("risk1", "channel1"),
            plan.activatedTasks().stream().map(NodeRunState::nodeKey).toList());
        Assertions.assertEquals(0, ledger.rounds("applePay"));
    }

    @Test
    void test_exclusiveConditionBranch() {
        reachCommerce();
        complete("commerce", variables("{\"paymentMethod\":\"APPLE_PAY\"}"));
        Assertions.assertEquals("e_apple", ledger.latest("applePayGate").selectedEdgeKey());
        assertRun("applePay", 1, NodeRunStatus.ACTIVE, null, ledger.latest("applePayGate").nodeRunId());
        Assertions.assertEquals(0, ledger.rounds("fork"));
        complete("applePay", variables("{\"paymentMethod\":\"APPLE_PAY\"}"));
        Assertions.assertEquals(ledger.latest("applePay").nodeRunId(), ledger.latest("fork").previousNodeRunId());
    }

    @Test
    void test_parallelJoinWaitsForEveryBranchThenEnds() {
        reachFork();
        complete("risk1", card());
        assertRun("risk2", 1, NodeRunStatus.APPROVING, "b_risk", ledger.latest("risk1").nodeRunId());
        NavigationPlan channelDone = complete("channel1", card());
        NodeRunState join = ledger.latest("join");
        Assertions.assertEquals(NodeRunStatus.WAITING, join.status());
        Assertions.assertNull(join.branchKey());
        Assertions.assertNull(join.previousNodeRunId());
        Assertions.assertTrue(channelDone.activatedTasks().isEmpty());

        NavigationPlan riskDone = complete("risk2", card());
        Assertions.assertEquals(NodeRunStatus.PASSED, ledger.latest("join").status());
        Assertions.assertTrue(riskDone.statusChanges().stream()
            .anyMatch(change -> change.nodeKey().equals("join") && change.toStatus() == NodeRunStatus.PASSED));
        assertRun("final", 1, NodeRunStatus.APPROVING, null, join.nodeRunId());
        Assertions.assertEquals(1, ledger.rounds("join"));

        NavigationPlan finished = complete("final", card());
        Assertions.assertTrue(finished.instanceCompleted());
        Assertions.assertEquals(NodeRunStatus.PASSED, ledger.latest("end").status());
        Assertions.assertTrue(ledger.completed());
    }

    @Test
    void test_returnForRevision() {
        reachCommerce();
        ledger.setStatus("commerce", NodeRunStatus.APPROVING);
        NodeRunState first = ledger.latest("commerce");
        NavigationPlan plan = ledger.apply(navigator.returnForRevision(graph, ledger.runs(), first.nodeRunId(),
            ledger::nextId));
        Assertions.assertEquals(NodeRunStatus.RETURNED, ledger.run(first.nodeRunId()).status());
        assertRun("commerce", 2, NodeRunStatus.ACTIVE, null, first.previousNodeRunId());
        Assertions.assertEquals(1, ledger.rounds("approve"));
        Assertions.assertEquals(1, plan.statusChanges().size());

        ledger = new NodeRunLedger();
        ledger.apply(navigator.start(graph, card(), ledger::nextId));
        NodeRunState approve = ledger.latest("approve");
        Assertions.assertEquals(NodeRunStatus.APPROVING, approve.status());
        Assertions.assertThrows(BizCheckFailException.class,
            () -> navigator.returnForRevision(graph, ledger.runs(), approve.nodeRunId(), ledger::nextId));
    }

    @Test
    void test_returnForRevisionOnBranchFirstNode() {
        reachFork();
        ledger.setStatus("risk1", NodeRunStatus.APPROVING);
        NodeRunState first = ledger.latest("risk1");
        ledger.apply(navigator.returnForRevision(graph, ledger.runs(), first.nodeRunId(), ledger::nextId));
        assertRun("risk1", 2, NodeRunStatus.ACTIVE, "b_risk", first.previousNodeRunId());
        Assertions.assertEquals(NodeRunStatus.ACTIVE, ledger.latest("channel1").status());
    }

    @Test
    void test_rejectFirstTask() {
        ledger.apply(navigator.start(graph, card(), ledger::nextId));
        NodeRunState approve = ledger.latest("approve");
        Assertions.assertEquals(RejectionKind.FIRST_TASK, option("approve").kind());
        Assertions.assertFalse(option("approve").allowed());
        Assertions.assertThrows(BizCheckFailException.class,
            () -> navigator.reject(graph, ledger.runs(), approve.nodeRunId(), null, ledger::nextId));
    }

    @Test
    void test_rejectToPreviousTask() {
        reachCommerce();
        ledger.setStatus("commerce", NodeRunStatus.APPROVING);
        Assertions.assertEquals(new RejectOption(RejectionKind.PREVIOUS_TASK, List.of("approve")), option("commerce"));
        NodeRunState commerce = ledger.latest("commerce");
        NodeRunState approve = ledger.latest("approve");
        Assertions.assertThrows(BizCheckFailException.class,
            () -> navigator.reject(graph, ledger.runs(), commerce.nodeRunId(), "risk1", ledger::nextId));
        ledger.apply(navigator.reject(graph, ledger.runs(), commerce.nodeRunId(), null, ledger::nextId));
        Assertions.assertEquals(NodeRunStatus.REJECTED, ledger.run(commerce.nodeRunId()).status());
        assertRun("approve", 2, NodeRunStatus.APPROVING, null, approve.previousNodeRunId());
    }

    @Test
    void test_rejectSkipsExclusiveGateway() {
        reachCommerce();
        complete("commerce", variables("{\"paymentMethod\":\"APPLE_PAY\"}"));
        ledger.setStatus("applePay", NodeRunStatus.APPROVING);
        Assertions.assertEquals(new RejectOption(RejectionKind.PREVIOUS_TASK, List.of("commerce")), option("applePay"));
        ledger.apply(navigator.reject(graph, ledger.runs(), ledger.latest("applePay").nodeRunId(), "commerce",
            ledger::nextId));
        assertRun("commerce", 2, NodeRunStatus.ACTIVE, null, ledger.latest("approve").nodeRunId());
    }

    @Test
    void test_rejectBranchFirstNodeNotAllowed() {
        reachFork();
        Assertions.assertEquals(RejectionKind.BRANCH_FIRST_NODE, option("risk1").kind());
        Assertions.assertEquals(RejectionKind.BRANCH_FIRST_NODE, option("channel1").kind());
        ledger.setStatus("channel1", NodeRunStatus.APPROVING);
        Assertions.assertThrows(BizCheckFailException.class, () -> navigator.reject(graph, ledger.runs(),
            ledger.latest("channel1").nodeRunId(), null, ledger::nextId));
    }

    @Test
    void test_rejectInsideBranchKeepsOtherBranch() {
        reachFork();
        complete("risk1", card());
        NodeRunState risk1 = ledger.latest("risk1");
        Assertions.assertEquals(new RejectOption(RejectionKind.PREVIOUS_TASK, List.of("risk1")), option("risk2"));
        ledger.apply(navigator.reject(graph, ledger.runs(), ledger.latest("risk2").nodeRunId(), null, ledger::nextId));
        Assertions.assertEquals(NodeRunStatus.REJECTED, ledger.latest("risk2").status());
        assertRun("risk1", 2, NodeRunStatus.ACTIVE, "b_risk", risk1.previousNodeRunId());
        Assertions.assertEquals(NodeRunStatus.ACTIVE, ledger.latest("channel1").status());
        Assertions.assertEquals(1, ledger.rounds("channel1"));
    }

    @Test
    void test_rejectAfterJoinChoosesBranch() {
        reachFinal();
        NodeRunState join = ledger.latest("join");
        NodeRunState channel1 = ledger.latest("channel1");
        Assertions.assertEquals(new RejectOption(RejectionKind.CHOOSE_BRANCH, List.of("risk2", "channel1")),
            option("final"));
        NodeRunState fin = ledger.latest("final");
        Assertions.assertThrows(BizCheckFailException.class,
            () -> navigator.reject(graph, ledger.runs(), fin.nodeRunId(), null, ledger::nextId));
        Assertions.assertThrows(BizCheckFailException.class,
            () -> navigator.reject(graph, ledger.runs(), fin.nodeRunId(), "approve", ledger::nextId));

        ledger.apply(navigator.reject(graph, ledger.runs(), fin.nodeRunId(), "channel1", ledger::nextId));
        Assertions.assertEquals(NodeRunStatus.REJECTED, ledger.latest("final").status());
        Assertions.assertEquals(NodeRunStatus.WAITING, ledger.run(join.nodeRunId()).status());
        assertRun("channel1", 2, NodeRunStatus.ACTIVE, "b_channel", channel1.previousNodeRunId());
        Assertions.assertEquals(1, ledger.rounds("risk2"));

        complete("channel1", card());
        Assertions.assertEquals(NodeRunStatus.PASSED, ledger.run(join.nodeRunId()).status());
        Assertions.assertEquals(1, ledger.rounds("join"));
        assertRun("final", 2, NodeRunStatus.APPROVING, null, join.nodeRunId());
    }

    @Test
    void test_cancelOpenRuns() {
        reachFork();
        complete("channel1", card());
        NavigationPlan plan = ledger.apply(navigator.cancelOpenRuns(graph, ledger.runs()));
        Assertions.assertEquals(List.of("risk1", "join"),
            plan.statusChanges().stream().map(change -> change.nodeKey()).toList());
        Assertions.assertTrue(ledger.runs().stream().noneMatch(run -> run.status() == NodeRunStatus.ACTIVE
            || run.status() == NodeRunStatus.APPROVING || run.status() == NodeRunStatus.WAITING));
        Assertions.assertTrue(plan.createdNodeRuns().isEmpty());
    }

    @Test
    void test_statusPreconditions() {
        reachCommerce();
        NodeRunState approve = ledger.latest("approve");
        Assertions.assertThrows(BizCheckFailException.class,
            () -> navigator.completeTask(graph, ledger.runs(), approve.nodeRunId(), card(), ledger::nextId));
        NodeRunState commerce = ledger.latest("commerce");
        Assertions.assertThrows(BizCheckFailException.class,
            () -> navigator.reject(graph, ledger.runs(), commerce.nodeRunId(), null, ledger::nextId));
        NodeRunState start = ledger.latest("start");
        Assertions.assertEquals(WorkflowNodeType.START, start.nodeType());
        Assertions.assertThrows(ErrorException.class,
            () -> navigator.completeTask(graph, ledger.runs(), start.nodeRunId(), card(), ledger::nextId));
        Assertions.assertThrows(ErrorException.class,
            () -> navigator.completeTask(graph, ledger.runs(), 1L, card(), ledger::nextId));
    }

    /** 发起并通过立项审批，停在 commerce（ACTIVE）。 */
    private void reachCommerce() {
        ledger.apply(navigator.start(graph, card(), ledger::nextId));
        complete("approve", card());
    }

    /** 走默认分支到并行分叉，risk1 与 channel1 均为 ACTIVE。 */
    private void reachFork() {
        reachCommerce();
        complete("commerce", card());
    }

    /** 两条分支都完成，停在 final（APPROVING）。 */
    private void reachFinal() {
        reachFork();
        complete("risk1", card());
        complete("risk2", card());
        complete("channel1", card());
    }

    /**
     * 放行节点最新一轮并应用计划。
     *
     * @param nodeKey 节点 key
     * @param variables 实例变量
     * @return 导航计划
     */
    private NavigationPlan complete(String nodeKey, WorkflowVariables variables) {
        return ledger.apply(navigator.completeTask(graph, ledger.runs(), ledger.latest(nodeKey).nodeRunId(), variables,
            ledger::nextId));
    }

    /**
     * 节点最新一轮的驳回选项。
     *
     * @param nodeKey 节点 key
     * @return 驳回选项
     */
    private RejectOption option(String nodeKey) {
        return navigator.rejectOption(graph, ledger.runs(), ledger.latest(nodeKey).nodeRunId());
    }

    /**
     * 断言节点最新一轮的轮次、状态、分支与来源。
     *
     * @param nodeKey 节点 key
     * @param round 轮次
     * @param status 状态
     * @param branchKey 分支
     * @param previousNodeRunId 来源
     */
    private void assertRun(String nodeKey, int round, NodeRunStatus status, String branchKey, Long previousNodeRunId) {
        NodeRunState run = ledger.latest(nodeKey);
        Assertions.assertEquals(round, run.roundNo(), nodeKey + " round");
        Assertions.assertEquals(status, run.status(), nodeKey + " status");
        Assertions.assertEquals(branchKey, run.branchKey(), nodeKey + " branch");
        Assertions.assertEquals(previousNodeRunId, run.previousNodeRunId(), nodeKey + " previous");
    }

    /**
     * 支付方式为 CARD 的启动变量。
     *
     * @return 实例变量
     */
    private WorkflowVariables card() {
        return variables("{\"paymentMethod\":\"CARD\",\"channelCode\":\"GEIDEA_SA\"}");
    }

    /**
     * 按启动表单基线组装 start.* 变量。
     *
     * @param startValues start_values_json
     * @return 实例变量
     */
    private WorkflowVariables variables(String startValues) {
        return WorkflowVariables.builder().add(VariableDeclaration.ofForm(VariableDeclaration.START_PREFIX,
            WorkflowFixtures.startForm()), JSON.readTree(startValues)).build();
    }
}

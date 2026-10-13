package com.paytech.risk.test.checker.decision;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.risk.decision.domain.enums.FlowStatus;
import com.paytech.risk.test.builder.decision.FlowBuilder;
import com.paytech.risk.test.builder.decision.FlowBuilder.EdgeSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.NodeSpec;
import com.paytech.risk.test.mapper.decision.FlowTestMapper;
import com.paytech.risk.test.mapper.decision.FlowTestMapper.EdgeRow;
import com.paytech.risk.test.mapper.decision.FlowTestMapper.NodeRow;
import com.paytech.risk.test.mapper.decision.FlowTestMapper.RootRow;
import com.paytech.risk.test.mapper.decision.FlowTestMapper.VersionRow;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlow;
import com.paytech.risk.test.mocker.decision.FlowMocker.MockedFlowVersion;

/**
 * 决策流四表落库事实的数据库 Checker
 * <li>从真实数据库读取根、版本、节点与连线，与期望的业务事实逐字段比较；只读不改数据。</li>
 *
 * @author Kenny
 */
@TestComponent
public class FlowStoreChecker {

    private final FlowTestMapper mapper;

    public FlowStoreChecker(FlowTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 期望的管理根事实。 */
    public record ExpectedRoot(long flowId, long currentFlowVersionId, FlowStatus status, long version, Long deletedBy,
        Instant deletedTime, Instant createTime, Instant updateTime) {
    }

    /** 期望的版本事实及其完整配置图。 */
    public record ExpectedVersion(long flowVersionId, int versionNo, String flowName, long createdBy,
        String changeReason, Instant createTime, GraphSpec graph) {
    }

    /** 插入版本的原始事实。 */
    public static ExpectedVersion inserted(MockedFlowVersion version) {
        return new ExpectedVersion(version.flowVersionId(), version.versionNo(), version.spec().flowName(),
            version.spec().createdBy(), version.spec().changeReason(), version.spec().createTime(),
            version.spec().graph());
    }

    /** 根与全部版本（按版本号升序）逐字段一致。 */
    public void assertFlow(String flowCode, ExpectedRoot root, List<ExpectedVersion> versions) {
        RootRow actual = mapper.root(flowCode).orElseThrow(() -> new AssertionError("流程根不存在：" + flowCode));
        assertAll("流程根 " + flowCode, () -> assertEquals(root.flowId(), actual.flowId(), "flowId"),
            () -> assertEquals(FlowTestMapper.DECISION_POINT_ID, actual.decisionPointId(), "decisionPointId"),
            () -> assertEquals(flowCode, actual.flowCode(), "flowCode"),
            () -> assertEquals(root.currentFlowVersionId(), actual.currentFlowVersionId(), "currentFlowVersionId"),
            () -> assertEquals(root.status().getCode(), actual.status(), "status"),
            () -> assertEquals(root.version(), actual.version(), "version"),
            () -> assertEquals(root.deletedBy(), actual.deletedBy(), "deletedBy"),
            () -> assertEquals(root.deletedTime(), actual.deletedTime(), "deletedTime"),
            () -> assertEquals(root.createTime(), actual.createTime(), "createTime"),
            () -> assertEquals(root.updateTime(), actual.updateTime(), "updateTime"));
        List<VersionRow> rows = mapper.versions(root.flowId());
        assertEquals(versions.size(), rows.size(), "版本数量不符");
        for (int i = 0; i < versions.size(); i++) {
            assertVersion(root.flowId(), versions.get(i), rows.get(i));
        }
    }

    /** 插入的流程（根、版本、节点与连线）保持原样，没有任何新增或改写。 */
    public void assertUnchanged(MockedFlow flow) {
        MockedFlowVersion head = flow.current();
        assertFlow(flow.flowCode(), new ExpectedRoot(flow.flowId(), head.flowVersionId(), flow.status(), flow.version(),
            flow.deleted() ? FlowBuilder.DELETED_BY : null, flow.deletedTime(), flow.createTime(), flow.updateTime()),
            flow.versions().stream().map(FlowStoreChecker::inserted).toList());
    }

    /** 给定编码的流程不存在，且决策点下流程总数与写入前一致。 */
    public void assertNoFlow(String flowCode, long expectedFlowCount, long expectedVersionCount) {
        assertAll("未落库", () -> assertTrue(mapper.root(flowCode).isEmpty(), "不应创建流程：" + flowCode),
            () -> assertEquals(expectedFlowCount, mapper.flowCount(), "流程总数"),
            () -> assertEquals(expectedVersionCount, mapper.versionCount(), "流程版本总数"));
    }

    private void assertVersion(long flowId, ExpectedVersion expected, VersionRow actual) {
        assertAll("版本 v" + expected.versionNo(),
            () -> assertEquals(expected.flowVersionId(), actual.flowVersionId(), "flowVersionId"),
            () -> assertEquals(flowId, actual.flowId(), "flowId"),
            () -> assertEquals(expected.versionNo(), actual.versionNo(), "versionNo"),
            () -> assertEquals(expected.flowName(), actual.flowName(), "flowName"),
            () -> assertEquals("ORDER_PREPARATION", actual.inputContractCode(), "inputContractCode"),
            () -> assertEquals(4, actual.inputContractVersion(), "inputContractVersion"),
            () -> assertEquals(expected.createdBy(), actual.createdBy(), "createdBy"),
            () -> assertEquals(expected.changeReason(), actual.changeReason(), "changeReason"),
            () -> assertEquals(expected.createTime(), actual.createTime(), "createTime"),
            () -> assertEquals(expected.createTime(), actual.updateTime(), "updateTime"));
        assertNodes(actual.flowVersionId(), expected.graph().nodes(), expected.createTime());
        assertEdges(actual.flowVersionId(), expected.graph().edges(), expected.createTime());
    }

    private void assertNodes(long versionId, List<NodeSpec> nodes, Instant time) {
        List<NodeSpec> expected = nodes.stream().sorted(Comparator.comparing(NodeSpec::nodeKey)).toList();
        List<NodeRow> actual = mapper.nodes(versionId);
        assertEquals(expected.size(), actual.size(), "节点数量不符");
        for (int i = 0; i < expected.size(); i++) {
            NodeSpec node = expected.get(i);
            NodeRow row = actual.get(i);
            assertAll("节点 " + node.nodeKey(), () -> assertTrue(row.nodeId() > 0, "nodeId"),
                () -> assertEquals(node.nodeKey(), row.nodeKey()),
                () -> assertEquals(node.type().getCode(), row.nodeType()),
                () -> assertEquals(node.name(), row.nodeName()),
                () -> assertEquals(node.ruleSetVersionId(), row.ruleSetVersionId()),
                () -> assertEquals(node.decision() == null ? null : node.decision().getCode(), row.decisionCode()),
                () -> assertEquals(node.reasonCode(), row.reasonCode()),
                () -> assertEquals(node.reasonMessage(), row.reasonMessage()),
                () -> assertEquals(node.x(), row.positionX()), () -> assertEquals(node.y(), row.positionY()),
                () -> assertEquals(time, row.createTime()), () -> assertEquals(time, row.updateTime()));
        }
    }

    private void assertEdges(long versionId, List<EdgeSpec> edges, Instant time) {
        List<EdgeSpec> expected = edges.stream().sorted(Comparator.comparing(EdgeSpec::edgeKey)).toList();
        List<EdgeRow> actual = mapper.edges(versionId);
        assertEquals(expected.size(), actual.size(), "连线数量不符");
        for (int i = 0; i < expected.size(); i++) {
            EdgeSpec edge = expected.get(i);
            EdgeRow row = actual.get(i);
            assertAll("连线 " + edge.edgeKey(), () -> assertTrue(row.edgeId() > 0, "edgeId"),
                () -> assertEquals(edge.edgeKey(), row.edgeKey()),
                () -> assertEquals(edge.sourceNodeKey(), row.sourceNodeKey()),
                () -> assertEquals(edge.targetNodeKey(), row.targetNodeKey()),
                () -> assertEquals(edge.type().getCode(), row.edgeType()),
                () -> assertEquals(edge.priority(), row.priorityNo()), () -> assertEquals(edge.name(), row.edgeName()),
                () -> assertEquals(edge.conditionJson(), row.conditionJson()),
                () -> assertEquals(time, row.createTime()), () -> assertEquals(time, row.updateTime()));
        }
    }
}

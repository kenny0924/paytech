package com.paytech.risk.test.builder.decision;

import com.paytech.risk.decision.api.DecisionFlowModels.Condition;
import com.paytech.risk.decision.api.DecisionFlowModels.Edge;
import com.paytech.risk.decision.api.DecisionFlowModels.Graph;
import com.paytech.risk.decision.api.DecisionFlowModels.Node;
import com.paytech.risk.decision.api.DecisionFlowModels.Position;
import com.paytech.risk.decision.api.request.SaveFlowRequestBody;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.test.builder.decision.FlowBuilder.EdgeSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.NodeSpec;

import tools.jackson.databind.json.JsonMapper;

/**
 * 保存/静态校验流程请求的测试 Builder
 * <li>{@link #template(String)} 为首次创建：两个编辑基准为 null，图为开始直连 ALLOW 结束。</li>
 * <li>图复用 {@link GraphSpec}，使请求与落库断言共用同一份期望；build() 不修复负例值。</li>
 *
 * @author Kenny
 */
public final class SaveFlowRequestBuilder {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private String pointCode = RuleSetBuilder.POINT_CODE;

    private String flowCode;

    private String flowName = "Checkout flow";

    private String baseFlowVersionId;

    private String expectedVersion;

    private String changeReason = "Initial configuration";

    private GraphSpec graph = GraphSpec.direct(DecisionValue.ALLOW);

    private SaveFlowRequestBuilder(String flowCode) {
        this.flowCode = flowCode;
    }

    /** 首次创建给定编码的最小合法流程。 */
    public static SaveFlowRequestBuilder template(String flowCode) {
        return new SaveFlowRequestBuilder(flowCode);
    }

    public SaveFlowRequestBuilder pointCode(String pointCode) {
        this.pointCode = pointCode;
        return this;
    }

    public SaveFlowRequestBuilder flowCode(String flowCode) {
        this.flowCode = flowCode;
        return this;
    }

    public SaveFlowRequestBuilder flowName(String flowName) {
        this.flowName = flowName;
        return this;
    }

    public SaveFlowRequestBuilder changeReason(String changeReason) {
        this.changeReason = changeReason;
        return this;
    }

    /** 编辑基准：调用方读取的当前版本 ID 与根修订号。 */
    public SaveFlowRequestBuilder base(long baseFlowVersionId, long expectedVersion) {
        return base(Long.toString(baseFlowVersionId), Long.toString(expectedVersion));
    }

    /** 编辑基准原始传输值，可为 null 或非法值。 */
    public SaveFlowRequestBuilder base(String baseFlowVersionId, String expectedVersion) {
        this.baseFlowVersionId = baseFlowVersionId;
        this.expectedVersion = expectedVersion;
        return this;
    }

    public SaveFlowRequestBuilder graph(GraphSpec graph) {
        this.graph = graph;
        return this;
    }

    public String flowName() {
        return flowName;
    }

    public String changeReason() {
        return changeReason;
    }

    public GraphSpec graphSpec() {
        return graph;
    }

    /** 正式请求 DTO；节点与连线保持 GraphSpec 中的顺序。 */
    public SaveFlowRequestBody build() {
        SaveFlowRequestBody body = new SaveFlowRequestBody();
        body.setPointCode(pointCode);
        body.setFlowCode(flowCode);
        body.setFlowName(flowName);
        body.setBaseFlowVersionId(baseFlowVersionId);
        body.setExpectedVersion(expectedVersion);
        body.setChangeReason(changeReason);
        body.setGraph(new Graph(1, graph.nodes().stream().map(SaveFlowRequestBuilder::node).toList(),
            graph.edges().stream().map(SaveFlowRequestBuilder::edge).toList()));
        return body;
    }

    private static Node node(NodeSpec node) {
        Node value = new Node(node.nodeKey(), node.type().getCode(), node.name(),
            new Position(Integer.toString(node.x()), Integer.toString(node.y())),
            node.ruleSetVersionId() == null ? null : node.ruleSetVersionId().toString(),
            node.decision() == null ? null : node.decision().getCode(), node.reasonCode(), node.reasonMessage());
        value.setOutputs(node.outputs().isEmpty() ? null : node.outputs());
        return value;
    }

    private static Edge edge(EdgeSpec edge) {
        return new Edge(edge.edgeKey(), edge.sourceNodeKey(), edge.targetNodeKey(), edge.type().getCode(),
            edge.priority(), edge.name(),
            edge.conditionJson() == null ? null : JSON.readValue(edge.conditionJson(), Condition.class));
    }
}

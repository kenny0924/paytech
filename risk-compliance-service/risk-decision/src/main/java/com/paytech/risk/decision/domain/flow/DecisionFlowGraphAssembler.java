package com.paytech.risk.decision.domain.flow;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowEdgeRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowNodeRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowRow;
import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowVersionRow;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Checked;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Edge;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Graph;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Node;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Save;

import lombok.AllArgsConstructor;

/** 只依赖已加载事实重建历史图，校验原结构且不查询数据库。 */
@Component
@AllArgsConstructor
public class DecisionFlowGraphAssembler {
    private final DecisionFlowJsonCodec codec;
    private final DecisionFlowValidator validator;

    /** 检查同版本节点与连线闭包，再沿用原严格图规则重建。 */
    public Graph assemble(String pointCode, DecisionFlowRow root, DecisionFlowVersionRow version,
        List<DecisionFlowNodeRow> rows, List<DecisionFlowEdgeRow> edgeRows) {
        root.verifyVersion(version);
        if (rows.size() > 50 || edgeRows.size() > 100) throw corrupt();
        Map<Long, String> keys = new HashMap<>();
        for (DecisionFlowNodeRow node : rows) {
            node.verifyVersion(version);
            if (keys.putIfAbsent(node.nodeId(), node.nodeKey()) != null) {
                throw corrupt();
            }
        }
        List<Node> nodes = rows.stream().map(n -> new Node(n.nodeKey(), n.nodeType().getCode(), n.nodeName(),
            n.positionX(), n.positionY(), n.ruleSetVersionId(), n.decisionCode() == null ? null : n.decisionCode().getCode(),
            n.reasonCode(), n.reasonMessage())).toList();
        List<Edge> edges = edgeRows.stream().map(e -> {
            e.verifyVersion(version);
            if (!keys.containsKey(e.sourceNodeId()) || !keys.containsKey(e.targetNodeId())) {
                throw corrupt();
            }
            return new Edge(e.edgeKey(), keys.get(e.sourceNodeId()), keys.get(e.targetNodeId()), e.edgeType().getCode(),
                e.priorityNo(), e.edgeName(), codec.readCondition(e.conditionJson()));
        }).sorted(Comparator.comparing(Edge::sourceNodeKey).thenComparingInt(Edge::priority).thenComparing(Edge::edgeKey)).toList();
        Graph graph = new Graph(1, nodes, edges);
        Checked checked = validator.check(new Save(pointCode, root.flowCode(),
            version.flowName(), null, null, version.changeReason(), graph));
        if (!checked.validation().valid() || !graph.equals(checked.normalized().graph())) throw corrupt();
        return graph;
    }

    /** 持久化损坏只报告安全错误，不回退为空图。 */
    private static ErrorException corrupt() { return new ErrorException("流程持久化配置不完整或身份不一致"); }
}

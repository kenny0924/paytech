package com.paytech.risk.decision.domain.flow;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.flow.DecisionFlow.*;

import lombok.AllArgsConstructor;

/** 只依赖已加载事实重建历史图，校验原结构且不查询数据库。 */
@Component
@AllArgsConstructor
public class DecisionFlowGraphAssembler {
    private final DecisionFlowJsonCodec codec;
    private final DecisionFlowValidator validator;

    /**
     * 检查同版本节点、连线与结束节点输出闭包，再沿用原严格图规则重建
     *
     * @param pointCode
     *            决策点编码
     * @param root
     *            流程根
     * @param version
     *            准确流程版本
     * @param rows
     *            该版本全部节点
     * @param edgeRows
     *            该版本全部连线
     * @param outputRows
     *            该版本全部结束节点输出
     * @return 与保存时一致的配置图；任一行不属于该版本或重建结果不一致时以系统错误抛出
     */
    public Graph assemble(String pointCode, DecisionFlowRow root, DecisionFlowVersionRow version,
        List<DecisionFlowNodeRow> rows, List<DecisionFlowEdgeRow> edgeRows, List<DecisionFlowEndOutput> outputRows) {
        root.verifyVersion(version);
        // 当前合同与登记的旧合同都可读取；执行发布另行要求当前合同。
        if (!DecisionPointAuthoringDescriptor.require(pointCode)
            .readsContract(version.inputContractCode(), version.inputContractVersion())) {
            throw corrupt();
        }
        if (rows.size() > 50 || edgeRows.size() > 100) throw corrupt();
        Map<Long, String> keys = new HashMap<>();
        for (DecisionFlowNodeRow node : rows) {
            node.verifyVersion(version);
            if (keys.putIfAbsent(node.nodeId(), node.nodeKey()) != null) {
                throw corrupt();
            }
        }
        Map<Long, Map<String, String>> outputs = outputs(version, keys, outputRows);
        List<Node> nodes = rows.stream().map(n -> new Node(n.nodeKey(), n.nodeType().getCode(), n.nodeName(),
            n.positionX(), n.positionY(), n.ruleSetVersionId(), n.decisionCode() == null ? null : n.decisionCode().getCode(),
            n.reasonCode(), n.reasonMessage(), outputs.getOrDefault(n.nodeId(), Map.of()))).toList();
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

    /** 输出行必须属于同版本的已知节点，同节点编码不重复。 */
    private static Map<Long, Map<String, String>> outputs(DecisionFlowVersionRow version, Map<Long, String> keys,
        List<DecisionFlowEndOutput> rows) {
        Map<Long, Map<String, String>> result = new HashMap<>();
        for (DecisionFlowEndOutput row : rows) {
            if (row.getFlowVersionId() != version.flowVersionId() || !keys.containsKey(row.getNodeId())
                || result.computeIfAbsent(row.getNodeId(), ignored -> new HashMap<>()).putIfAbsent(row.getOutputCode(),
                    row.getOutputValue()) != null) {
                throw corrupt();
            }
        }
        return result;
    }

    /** 持久化损坏只报告安全错误，不回退为空图。 */
    private static ErrorException corrupt() { return new ErrorException("流程持久化配置不完整或身份不一致"); }
}

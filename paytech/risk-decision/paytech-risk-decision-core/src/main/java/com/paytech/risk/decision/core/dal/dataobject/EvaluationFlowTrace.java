package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.FlowNodeType;
import java.time.Instant;

import lombok.Data;

/** 一次评估实际经过的决策流节点（t_evaluation_flow_trace）。 */
@Data
public class EvaluationFlowTrace {
    /** 轨迹 ID。 */
    private long traceId;

    /** 评估 ID。 */
    private long evaluationId;

    /** 经过顺序，从 1 开始；开始节点为 1，结束节点为最后一行。 */
    private int stepNo;

    /** 评估所用流程版本内的节点键。 */
    private String nodeKey;

    /** 节点类型。 */
    private FlowNodeType nodeType;

    /** 规则集节点引用的规则集版本 ID；其他节点为空。 */
    private Long ruleSetVersionId;

    /** 规则集节点是否有规则命中；其他节点为空。 */
    private Boolean matchedFlag;

    /**
     * 节点决定
     * <li>规则集节点：首个终态命中的决定，没有终态命中为空。</li>
     * <li>结束节点：该节点的结论。</li>
     */
    private DecisionValue nodeDecision;

    /** 规则集节点是否复用同一规则集版本此前的结果；其他节点为空。 */
    private Boolean reusedFlag;

    /** 离开本节点所走的连线键；结束节点为空。 */
    private String exitEdgeKey;

    /** 写入时间。 */
    private Instant createTime;
}

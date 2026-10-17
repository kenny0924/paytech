package com.paytech.risk.api.evaluation.response;

/** 评估经过的一个流程节点。 */
public class EvaluationPathStep {

    /** 经过顺序，从 1 开始。 */
    private int stepNo;

    /** 流程版本内的节点键。 */
    private String nodeKey;

    /** 节点类型：START、RULE_SET、END。 */
    private String nodeType;

    /** 节点名称。 */
    private String nodeName;

    /**
     * 规则集版本 ID，十进制字符串。
     * <li>仅规则集节点有值。</li>
     */
    private String ruleSetVersionId;

    /**
     * 规则集编码。
     * <li>仅规则集节点有值。</li>
     */
    private String ruleSetCode;

    /**
     * 规则集版本号。
     * <li>仅规则集节点有值。</li>
     */
    private Integer ruleSetVersionNo;

    /**
     * 规则集节点是否有规则命中。
     * <li>其他节点为空。</li>
     */
    private Boolean matched;

    /**
     * 节点决定。
     * <li>规则集节点为首个终态命中的决定（没有为空）；结束节点为该节点的结论。</li>
     */
    private String decision;

    /**
     * 规则集节点是否复用了同一规则集版本此前的结果。
     * <li>其他节点为空。</li>
     */
    private Boolean reused;

    /**
     * 离开本节点所走的连线键。
     * <li>结束节点为空。</li>
     */
    private String exitEdgeKey;

    /**
     * 该连线的名称。
     * <li>未命名或结束节点为空。</li>
     */
    private String exitEdgeName;

    /** 返回经过顺序。 */
    public int getStepNo() { return stepNo; }

    /** 设置经过顺序。 */
    public void setStepNo(int stepNo) { this.stepNo = stepNo; }

    /** 返回流程版本内的节点键。 */
    public String getNodeKey() { return nodeKey; }

    /** 设置流程版本内的节点键。 */
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }

    /** 返回节点类型。 */
    public String getNodeType() { return nodeType; }

    /** 设置节点类型。 */
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    /** 返回节点名称。 */
    public String getNodeName() { return nodeName; }

    /** 设置节点名称。 */
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }

    /** 返回规则集版本 ID。 */
    public String getRuleSetVersionId() { return ruleSetVersionId; }

    /** 设置规则集版本 ID。 */
    public void setRuleSetVersionId(String ruleSetVersionId) { this.ruleSetVersionId = ruleSetVersionId; }

    /** 返回规则集编码。 */
    public String getRuleSetCode() { return ruleSetCode; }

    /** 设置规则集编码。 */
    public void setRuleSetCode(String ruleSetCode) { this.ruleSetCode = ruleSetCode; }

    /** 返回规则集版本号。 */
    public Integer getRuleSetVersionNo() { return ruleSetVersionNo; }

    /** 设置规则集版本号。 */
    public void setRuleSetVersionNo(Integer ruleSetVersionNo) { this.ruleSetVersionNo = ruleSetVersionNo; }

    /** 返回规则集节点是否有规则命中。 */
    public Boolean getMatched() { return matched; }

    /** 设置规则集节点是否有规则命中。 */
    public void setMatched(Boolean matched) { this.matched = matched; }

    /** 返回节点决定。 */
    public String getDecision() { return decision; }

    /** 设置节点决定。 */
    public void setDecision(String decision) { this.decision = decision; }

    /** 返回规则集节点是否复用了同一规则集版本此前的结果。 */
    public Boolean getReused() { return reused; }

    /** 设置规则集节点是否复用了同一规则集版本此前的结果。 */
    public void setReused(Boolean reused) { this.reused = reused; }

    /** 返回离开本节点所走的连线键。 */
    public String getExitEdgeKey() { return exitEdgeKey; }

    /** 设置离开本节点所走的连线键。 */
    public void setExitEdgeKey(String exitEdgeKey) { this.exitEdgeKey = exitEdgeKey; }

    /** 返回该连线的名称。 */
    public String getExitEdgeName() { return exitEdgeName; }

    /** 设置该连线的名称。 */
    public void setExitEdgeName(String exitEdgeName) { this.exitEdgeName = exitEdgeName; }
}

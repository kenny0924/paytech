package com.paytech.risk.api.evaluation.response;

/** 一个条件谓词的求值结果；条件结构以规则集版本详情为准。 */
public class EvaluationPredicateItem {

    /** 条件节点 ID，十进制字符串。 */
    private String nodeId;

    /** 求值结果。 */
    private boolean result;

    /** 返回条件节点 ID。 */
    public String getNodeId() { return nodeId; }

    /** 设置条件节点 ID。 */
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    /** 返回求值结果。 */
    public boolean isResult() { return result; }

    /** 设置求值结果。 */
    public void setResult(boolean result) { this.result = result; }
}

package com.paytech.risk.decision.api.response;

import java.util.List;

import com.paytech.risk.decision.api.DecisionFlowModels.Edge;
import com.paytech.risk.decision.api.DecisionFlowModels.Node;
import com.paytech.risk.decision.api.DecisionFlowModels.RuleSetReference;

/** 旧顺序 Point Version 的只读投影；无 Flow 身份，不接收新图保存。 */
public class LegacyPointResponseBody {

    /** 真实引用来源类型，FLOW_VERSION 或 LEGACY_POINT。 */
    private String sourceType;

    /** 旧顺序配置的准确版本 ID，不是新 Flow 身份。 */
    private String pointVersionId;

    /** 决策点编码。 */
    private String pointCode;

    /** 决策点名称。 */
    private String pointName;

    /** 所选版本的业务版本号。 */
    private int versionNo;

    /** 准确输入合同编码。 */
    private String inputContractCode;

    /** 准确输入合同版本号。 */
    private int inputContractVersion;

    /** 原版本默认决定，不重新计算。 */
    private String defaultDecision;

    /** 原版本评估策略。 */
    private String evaluationStrategy;

    /** 原版本失败策略。 */
    private String failureStrategy;

    /** 按原绑定顺序投影的只读节点。 */
    private List<Node> nodes;

    /** 按原绑定顺序投影的只读连线。 */
    private List<Edge> edges;

    /** 所选图中每个规则集节点的准确版本与当前管理资格。 */
    private List<RuleSetReference> ruleSetReferences;

    /** 供 JSON 属性绑定使用。 */
    public LegacyPointResponseBody() { }

    /** 根据完整查询事实构造响应。 */
    public LegacyPointResponseBody(String sourceType, String pointVersionId, String pointCode, String pointName, int versionNo, String inputContractCode, int inputContractVersion, String defaultDecision, String evaluationStrategy, String failureStrategy, List<Node> nodes, List<Edge> edges, List<RuleSetReference> ruleSetReferences) {
        this.sourceType = sourceType;
        this.pointVersionId = pointVersionId;
        this.pointCode = pointCode;
        this.pointName = pointName;
        this.versionNo = versionNo;
        this.inputContractCode = inputContractCode;
        this.inputContractVersion = inputContractVersion;
        this.defaultDecision = defaultDecision;
        this.evaluationStrategy = evaluationStrategy;
        this.failureStrategy = failureStrategy;
        this.nodes = nodes;
        this.edges = edges;
        this.ruleSetReferences = ruleSetReferences;
    }

    /** 返回真实引用来源类型，FLOW_VERSION 或 LEGACY_POINT。 */
    public String getSourceType() { return sourceType; }

    /** 设置真实引用来源类型，FLOW_VERSION 或 LEGACY_POINT。 */
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    /** 返回旧顺序配置的准确版本 ID，不是新 Flow 身份。 */
    public String getPointVersionId() { return pointVersionId; }

    /** 设置旧顺序配置的准确版本 ID，不是新 Flow 身份。 */
    public void setPointVersionId(String pointVersionId) { this.pointVersionId = pointVersionId; }

    /** 返回决策点编码。 */
    public String getPointCode() { return pointCode; }

    /** 设置决策点编码。 */
    public void setPointCode(String pointCode) { this.pointCode = pointCode; }

    /** 返回决策点名称。 */
    public String getPointName() { return pointName; }

    /** 设置决策点名称。 */
    public void setPointName(String pointName) { this.pointName = pointName; }

    /** 返回所选版本的业务版本号。 */
    public int getVersionNo() { return versionNo; }

    /** 设置所选版本的业务版本号。 */
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }

    /** 返回准确输入合同编码。 */
    public String getInputContractCode() { return inputContractCode; }

    /** 设置准确输入合同编码。 */
    public void setInputContractCode(String inputContractCode) { this.inputContractCode = inputContractCode; }

    /** 返回准确输入合同版本号。 */
    public int getInputContractVersion() { return inputContractVersion; }

    /** 设置准确输入合同版本号。 */
    public void setInputContractVersion(int inputContractVersion) { this.inputContractVersion = inputContractVersion; }

    /** 返回原版本默认决定，不重新计算。 */
    public String getDefaultDecision() { return defaultDecision; }

    /** 设置原版本默认决定，不重新计算。 */
    public void setDefaultDecision(String defaultDecision) { this.defaultDecision = defaultDecision; }

    /** 返回原版本评估策略。 */
    public String getEvaluationStrategy() { return evaluationStrategy; }

    /** 设置原版本评估策略。 */
    public void setEvaluationStrategy(String evaluationStrategy) { this.evaluationStrategy = evaluationStrategy; }

    /** 返回原版本失败策略。 */
    public String getFailureStrategy() { return failureStrategy; }

    /** 设置原版本失败策略。 */
    public void setFailureStrategy(String failureStrategy) { this.failureStrategy = failureStrategy; }

    /** 返回按原绑定顺序投影的只读节点。 */
    public List<Node> getNodes() { return nodes; }

    /** 设置按原绑定顺序投影的只读节点。 */
    public void setNodes(List<Node> nodes) { this.nodes = nodes; }

    /** 返回按原绑定顺序投影的只读连线。 */
    public List<Edge> getEdges() { return edges; }

    /** 设置按原绑定顺序投影的只读连线。 */
    public void setEdges(List<Edge> edges) { this.edges = edges; }

    /** 返回所选图中每个规则集节点的准确版本与当前管理资格。 */
    public List<RuleSetReference> getRuleSetReferences() { return ruleSetReferences; }

    /** 设置所选图中每个规则集节点的准确版本与当前管理资格。 */
    public void setRuleSetReferences(List<RuleSetReference> ruleSetReferences) { this.ruleSetReferences = ruleSetReferences; }
}

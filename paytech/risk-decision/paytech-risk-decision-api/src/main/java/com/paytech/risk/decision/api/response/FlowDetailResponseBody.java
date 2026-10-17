package com.paytech.risk.decision.api.response;

import java.util.List;

import com.paytech.risk.decision.api.DecisionFlowModels.Graph;
import com.paytech.risk.decision.api.DecisionFlowModels.RuleSetReference;
import com.paytech.risk.decision.api.RiskVariableModels;

/** 当前根事实与查询版本事实分别返回；删除根仅可通过准确历史入口读取。 */
public class FlowDetailResponseBody {

    /** 流程管理根 ID，以十进制字符串传输。 */
    private String flowId;

    /** 决策点编码。 */
    private String pointCode;

    /** 决策点内永久唯一的流程编码。 */
    private String flowCode;

    /** 流程管理状态，CONFIGURABLE 或 DELETED。 */
    private String flowStatus;

    /** 当前配置版本 ID，以十进制字符串传输。 */
    private String currentFlowVersionId;

    /** 当前最高业务版本号。 */
    private int currentVersionNo;

    /** 管理根修订号，以十进制字符串传输。 */
    private String version;

    /** 所选准确流程版本 ID，以十进制字符串传输。 */
    private String flowVersionId;

    /** 所选版本的业务版本号。 */
    private int versionNo;

    /** 所选版本保存时的流程名称。 */
    private String flowName;

    /** 准确输入合同编码。 */
    private String inputContractCode;

    /** 准确输入合同版本号。 */
    private int inputContractVersion;

    /** 所选版本保存人的 Account ID。 */
    private String createdBy;

    /** 所选版本的保存时间，UTC 微秒。 */
    private String createTime;

    /** 所选版本保存时填写的变更原因。 */
    private String changeReason;

    /** 所选不可变版本的完整配置图。 */
    private Graph graph;

    /** 所选图中每个规则集节点的准确版本与当前管理资格。 */
    private List<RuleSetReference> ruleSetReferences;

    /** 查询时的共享变量当前值，不改写历史图。 */
    private List<RiskVariableModels.VariableSummary> currentVariables;

    /** 当前共享值的查询时刻，UTC 微秒。 */
    private String valuesAsOf;

    /** 当前变量不可用的问题，不伪造历史配置执行结果。 */
    private List<RiskVariableModels.VariableIssue> variableIssues;

    /** 供 JSON 属性绑定使用。 */
    public FlowDetailResponseBody() { }

    /** 根据完整查询事实构造响应。 */
    public FlowDetailResponseBody(String flowId, String pointCode, String flowCode, String flowStatus, String currentFlowVersionId, int currentVersionNo, String version, String flowVersionId, int versionNo, String flowName, String inputContractCode, int inputContractVersion, String createdBy, String createTime, String changeReason, Graph graph, List<RuleSetReference> ruleSetReferences, List<RiskVariableModels.VariableSummary> currentVariables, String valuesAsOf, List<RiskVariableModels.VariableIssue> variableIssues) {
        this.flowId = flowId;
        this.pointCode = pointCode;
        this.flowCode = flowCode;
        this.flowStatus = flowStatus;
        this.currentFlowVersionId = currentFlowVersionId;
        this.currentVersionNo = currentVersionNo;
        this.version = version;
        this.flowVersionId = flowVersionId;
        this.versionNo = versionNo;
        this.flowName = flowName;
        this.inputContractCode = inputContractCode;
        this.inputContractVersion = inputContractVersion;
        this.createdBy = createdBy;
        this.createTime = createTime;
        this.changeReason = changeReason;
        this.graph = graph;
        this.ruleSetReferences = ruleSetReferences;
        this.currentVariables = currentVariables;
        this.valuesAsOf = valuesAsOf;
        this.variableIssues = variableIssues;
    }

    /** 返回流程管理根 ID，以十进制字符串传输。 */
    public String getFlowId() { return flowId; }

    /** 设置流程管理根 ID，以十进制字符串传输。 */
    public void setFlowId(String flowId) { this.flowId = flowId; }

    /** 返回决策点编码。 */
    public String getPointCode() { return pointCode; }

    /** 设置决策点编码。 */
    public void setPointCode(String pointCode) { this.pointCode = pointCode; }

    /** 返回决策点内永久唯一的流程编码。 */
    public String getFlowCode() { return flowCode; }

    /** 设置决策点内永久唯一的流程编码。 */
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }

    /** 返回流程管理状态，CONFIGURABLE 或 DELETED。 */
    public String getFlowStatus() { return flowStatus; }

    /** 设置流程管理状态，CONFIGURABLE 或 DELETED。 */
    public void setFlowStatus(String flowStatus) { this.flowStatus = flowStatus; }

    /** 返回当前配置版本 ID，以十进制字符串传输。 */
    public String getCurrentFlowVersionId() { return currentFlowVersionId; }

    /** 设置当前配置版本 ID，以十进制字符串传输。 */
    public void setCurrentFlowVersionId(String currentFlowVersionId) { this.currentFlowVersionId = currentFlowVersionId; }

    /** 返回当前最高业务版本号。 */
    public int getCurrentVersionNo() { return currentVersionNo; }

    /** 设置当前最高业务版本号。 */
    public void setCurrentVersionNo(int currentVersionNo) { this.currentVersionNo = currentVersionNo; }

    /** 返回管理根修订号，以十进制字符串传输。 */
    public String getVersion() { return version; }

    /** 设置管理根修订号，以十进制字符串传输。 */
    public void setVersion(String version) { this.version = version; }

    /** 返回所选准确流程版本 ID，以十进制字符串传输。 */
    public String getFlowVersionId() { return flowVersionId; }

    /** 设置所选准确流程版本 ID，以十进制字符串传输。 */
    public void setFlowVersionId(String flowVersionId) { this.flowVersionId = flowVersionId; }

    /** 返回所选版本的业务版本号。 */
    public int getVersionNo() { return versionNo; }

    /** 设置所选版本的业务版本号。 */
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }

    /** 返回所选版本保存时的流程名称。 */
    public String getFlowName() { return flowName; }

    /** 设置所选版本保存时的流程名称。 */
    public void setFlowName(String flowName) { this.flowName = flowName; }

    /** 返回准确输入合同编码。 */
    public String getInputContractCode() { return inputContractCode; }

    /** 设置准确输入合同编码。 */
    public void setInputContractCode(String inputContractCode) { this.inputContractCode = inputContractCode; }

    /** 返回准确输入合同版本号。 */
    public int getInputContractVersion() { return inputContractVersion; }

    /** 设置准确输入合同版本号。 */
    public void setInputContractVersion(int inputContractVersion) { this.inputContractVersion = inputContractVersion; }

    /** 返回所选版本保存人的 Account ID。 */
    public String getCreatedBy() { return createdBy; }

    /** 设置所选版本保存人的 Account ID。 */
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    /** 返回所选版本的保存时间，UTC 微秒。 */
    public String getCreateTime() { return createTime; }

    /** 设置所选版本的保存时间，UTC 微秒。 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }

    /** 返回所选版本保存时填写的变更原因。 */
    public String getChangeReason() { return changeReason; }

    /** 设置所选版本保存时填写的变更原因。 */
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    /** 返回所选不可变版本的完整配置图。 */
    public Graph getGraph() { return graph; }

    /** 设置所选不可变版本的完整配置图。 */
    public void setGraph(Graph graph) { this.graph = graph; }

    /** 返回所选图中每个规则集节点的准确版本与当前管理资格。 */
    public List<RuleSetReference> getRuleSetReferences() { return ruleSetReferences; }

    /** 设置所选图中每个规则集节点的准确版本与当前管理资格。 */
    public void setRuleSetReferences(List<RuleSetReference> ruleSetReferences) { this.ruleSetReferences = ruleSetReferences; }

    /** 返回查询时的共享变量当前值，不改写历史图。 */
    public List<RiskVariableModels.VariableSummary> getCurrentVariables() { return currentVariables; }

    /** 设置查询时的共享变量当前值，不改写历史图。 */
    public void setCurrentVariables(List<RiskVariableModels.VariableSummary> currentVariables) { this.currentVariables = currentVariables; }

    /** 返回当前共享值的查询时刻，UTC 微秒。 */
    public String getValuesAsOf() { return valuesAsOf; }

    /** 设置当前共享值的查询时刻，UTC 微秒。 */
    public void setValuesAsOf(String valuesAsOf) { this.valuesAsOf = valuesAsOf; }

    /** 返回当前变量不可用的问题，不伪造历史配置执行结果。 */
    public List<RiskVariableModels.VariableIssue> getVariableIssues() { return variableIssues; }

    /** 设置当前变量不可用的问题，不伪造历史配置执行结果。 */
    public void setVariableIssues(List<RiskVariableModels.VariableIssue> variableIssues) { this.variableIssues = variableIssues; }
}

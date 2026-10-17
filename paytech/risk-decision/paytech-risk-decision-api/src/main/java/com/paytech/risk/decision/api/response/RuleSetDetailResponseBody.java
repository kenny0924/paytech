package com.paytech.risk.decision.api.response;

import static com.paytech.risk.decision.api.DecisionRuleSetManagementModels.RuleRequestBody;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels.RuleRequestBody;
import com.paytech.risk.decision.api.RiskVariableModels;

/** 规则集管理响应。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class RuleSetDetailResponseBody {

    /** 准确规则集版本 ID。 */
    private String ruleSetVersionId;

    /** 已有旧顺序编排的准确 ID；新配置为 null，JSON 键始终保留。 */
    private String pointVersionId;

    /** 决策事件点编码。 */
    private String pointCode;

    /** 规则集编码。 */
    private String ruleSetCode;

    /** 规则集名称。 */
    private String ruleSetName;

    /** 业务版本号。 */
    private Integer versionNo;

    /** 当前状态。 */
    private String status;

    /** 版本内容 Hash。 */
    private String contentHash;

    /** 创建操作者 ID。 */
    private String createdBy;

    /** 本次变更原因。 */
    private String changeReason;

    /** UTC 微秒创建时间。 */
    private String createTime;

    /** 冻结操作者 ID；未冻结为 null。 */
    private String frozenBy;

    /** UTC 微秒冻结时间；未冻结为 null。 */
    private String frozenTime;

    /** 按优先级配置的规则。 */
    private List<RuleRequestBody> rules;

    /** 操作数格式；新保存使用 VARIABLE_REFERENCE。 */
    private String operandFormat;

    /** 当前变量展示值，不参与版本 Hash。 */
    private List<RiskVariableModels.VariableSummary> currentVariables;

    /** 当前变量读取时间。 */
    private String valuesAsOf;

    /** 当前变量不可用原因。 */
    private List<RiskVariableModels.VariableIssue> variableIssues;

    /** JSON 反序列化构造。 */
    public RuleSetDetailResponseBody() { }

    /** 按完整合同构造。 */
    public RuleSetDetailResponseBody(String ruleSetVersionId, String pointVersionId, String pointCode, String ruleSetCode, String ruleSetName, Integer versionNo, String status, String contentHash, String createdBy, String changeReason, String createTime, String frozenBy, String frozenTime, List<RuleRequestBody> rules, String operandFormat, List<RiskVariableModels.VariableSummary> currentVariables, String valuesAsOf, List<RiskVariableModels.VariableIssue> variableIssues) {
        setRuleSetVersionId(ruleSetVersionId);
        setPointVersionId(pointVersionId);
        setPointCode(pointCode);
        setRuleSetCode(ruleSetCode);
        setRuleSetName(ruleSetName);
        setVersionNo(versionNo);
        setStatus(status);
        setContentHash(contentHash);
        setCreatedBy(createdBy);
        setChangeReason(changeReason);
        setCreateTime(createTime);
        setFrozenBy(frozenBy);
        setFrozenTime(frozenTime);
        setRules(rules);
        setOperandFormat(operandFormat);
        setCurrentVariables(currentVariables);
        setValuesAsOf(valuesAsOf);
        setVariableIssues(variableIssues);
    }

    /** 按明确的历史字段组合构造，未提供的新字段保持空值。 */
    public RuleSetDetailResponseBody(String ruleSetVersionId, String pointVersionId, String pointCode,
        String ruleSetCode, String ruleSetName, Integer versionNo, String status, String contentHash,
        String createdBy, String changeReason, String createTime, String frozenBy, String frozenTime, List<RuleRequestBody> rules) {
        this(ruleSetVersionId, pointVersionId, pointCode, ruleSetCode, ruleSetName, versionNo, status, contentHash,
            createdBy, changeReason, createTime, frozenBy, frozenTime, rules, null, List.of(), null, List.of());
    }

    /** 返回准确规则集版本 ID。 */
    public String getRuleSetVersionId() { return ruleSetVersionId; }

    /** 设置准确规则集版本 ID。 */
    public void setRuleSetVersionId(String ruleSetVersionId) {
        this.ruleSetVersionId = ruleSetVersionId;
    }

    /** 返回已有旧顺序编排的准确 ID；新配置为 null，JSON 键始终保留。 */
    public String getPointVersionId() { return pointVersionId; }

    /** 设置已有旧顺序编排的准确 ID；新配置为 null，JSON 键始终保留。 */
    public void setPointVersionId(String pointVersionId) {
        this.pointVersionId = pointVersionId;
    }

    /** 返回决策事件点编码。 */
    public String getPointCode() { return pointCode; }

    /** 设置决策事件点编码。 */
    public void setPointCode(String pointCode) {
        this.pointCode = pointCode;
    }

    /** 返回规则集编码。 */
    public String getRuleSetCode() { return ruleSetCode; }

    /** 设置规则集编码。 */
    public void setRuleSetCode(String ruleSetCode) {
        this.ruleSetCode = ruleSetCode;
    }

    /** 返回规则集名称。 */
    public String getRuleSetName() { return ruleSetName; }

    /** 设置规则集名称。 */
    public void setRuleSetName(String ruleSetName) {
        this.ruleSetName = ruleSetName;
    }

    /** 返回业务版本号。 */
    public Integer getVersionNo() { return versionNo; }

    /** 设置业务版本号。 */
    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    /** 返回当前状态。 */
    public String getStatus() { return status; }

    /** 设置当前状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 返回版本内容 Hash。 */
    public String getContentHash() { return contentHash; }

    /** 设置版本内容 Hash。 */
    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    /** 返回创建操作者 ID。 */
    public String getCreatedBy() { return createdBy; }

    /** 设置创建操作者 ID。 */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /** 返回本次变更原因。 */
    public String getChangeReason() { return changeReason; }

    /** 设置本次变更原因。 */
    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    /** 返回UTC 微秒创建时间。 */
    public String getCreateTime() { return createTime; }

    /** 设置UTC 微秒创建时间。 */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    /** 返回冻结操作者 ID；未冻结为 null。 */
    public String getFrozenBy() { return frozenBy; }

    /** 设置冻结操作者 ID；未冻结为 null。 */
    public void setFrozenBy(String frozenBy) {
        this.frozenBy = frozenBy;
    }

    /** 返回UTC 微秒冻结时间；未冻结为 null。 */
    public String getFrozenTime() { return frozenTime; }

    /** 设置UTC 微秒冻结时间；未冻结为 null。 */
    public void setFrozenTime(String frozenTime) {
        this.frozenTime = frozenTime;
    }

    /** 返回按优先级配置的规则。 */
    public List<RuleRequestBody> getRules() { return rules; }

    /** 设置按优先级配置的规则。 */
    public void setRules(List<RuleRequestBody> rules) {
        this.rules = rules == null ? null : List.copyOf(rules);
    }

    /** 返回操作数格式；新保存使用 VARIABLE_REFERENCE。 */
    public String getOperandFormat() { return operandFormat; }

    /** 设置操作数格式；新保存使用 VARIABLE_REFERENCE。 */
    public void setOperandFormat(String operandFormat) {
        this.operandFormat = operandFormat;
    }

    /** 返回当前变量展示值，不参与版本 Hash。 */
    public List<RiskVariableModels.VariableSummary> getCurrentVariables() { return currentVariables; }

    /** 设置当前变量展示值，不参与版本 Hash。 */
    public void setCurrentVariables(List<RiskVariableModels.VariableSummary> currentVariables) {
        this.currentVariables = currentVariables == null ? null : List.copyOf(currentVariables);
    }

    /** 返回当前变量读取时间。 */
    public String getValuesAsOf() { return valuesAsOf; }

    /** 设置当前变量读取时间。 */
    public void setValuesAsOf(String valuesAsOf) {
        this.valuesAsOf = valuesAsOf;
    }

    /** 返回当前变量不可用原因。 */
    public List<RiskVariableModels.VariableIssue> getVariableIssues() { return variableIssues; }

    /** 设置当前变量不可用原因。 */
    public void setVariableIssues(List<RiskVariableModels.VariableIssue> variableIssues) {
        this.variableIssues = variableIssues == null ? null : List.copyOf(variableIssues);
    }
}

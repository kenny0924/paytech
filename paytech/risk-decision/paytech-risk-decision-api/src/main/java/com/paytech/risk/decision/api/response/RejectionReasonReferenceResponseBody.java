package com.paytech.risk.api.decision.response;

import java.util.List;

/** 精确保存版本的字典引用，历史名称与当前发布关系分开表达。 */
public class RejectionReasonReferenceResponseBody {

    /**
     * 规则集整体根 ID。
     * <li>正 BIGINT 字符串；流程来源为空。</li>
     */
    private String ruleSetId;

    /** 所属决策点编码。 */
    private String pointCode;

    /**
     * 规则集永久编码。
     * <li>流程来源为空。</li>
     */
    private String ruleSetCode;

    /**
     * 该版本保存的规则集名称。
     * <li>流程来源为空。</li>
     */
    private String ruleSetName;

    /**
     * 精确规则版本 ID。
     * <li>正 BIGINT 字符串；流程来源为空。</li>
     */
    private String ruleSetVersionId;

    /**
     * 规则业务版本号。
     * <li>流程来源为空。</li>
     */
    private Integer versionNo;

    /**
     * 版本内规则编码。
     * <li>流程来源为空。</li>
     */
    private String ruleCode;

    /**
     * 该版本保存的规则名称。
     * <li>流程来源为空。</li>
     */
    private String ruleName;

    /**
     * 引用字典类型。
     * <li>固定为 REJECTION_REASON。</li>
     */
    private String referenceKind;

    /**
     * 标签持久化动作序号。
     * <li>原因引用为空。</li>
     */
    private Integer actionSequenceNo;

    /**
     * 标签工程主体绑定。
     * <li>原因引用为空。</li>
     */
    private String targetBindingCode;

    /**
     * 规则集整体管理状态。
     * <li>流程来源为空。</li>
     */
    private String ruleSetStatus;

    /**
     * 保存版本是否为当前根指针。
     * <li>删除根也可能为真。</li>
     */
    private boolean isCurrentConfiguration;

    /**
     * 精确规则版本的活动环境。
     * <li>按编码排序去重；流程来源为空列表。</li>
     */
    private List<String> activeEnvironments;

    /**
     * 原因来源。
     * <li>RULE_SET 或 FLOW_END，两类专属字段互斥。</li>
     */
    private String sourceType;

    /**
     * 流程整体根 ID。
     * <li>正 BIGINT 字符串；规则来源为空。</li>
     */
    private String flowId;

    /**
     * 流程永久编码。
     * <li>规则来源为空。</li>
     */
    private String flowCode;

    /**
     * 该版本保存的流程名称。
     * <li>规则来源为空。</li>
     */
    private String flowName;

    /**
     * 精确流程版本 ID。
     * <li>正 BIGINT 字符串；规则来源为空。</li>
     */
    private String flowVersionId;

    /**
     * 流程业务版本号。
     * <li>不复用规则版本号；规则来源为空。</li>
     */
    private Integer flowVersionNo;

    /**
     * 保存时的稳定节点键。
     * <li>规则来源为空。</li>
     */
    private String nodeKey;

    /**
     * 保存时的节点名称。
     * <li>规则来源为空。</li>
     */
    private String nodeName;

    /**
     * 流程整体管理状态。
     * <li>规则来源为空。</li>
     */
    private String flowStatus;

    /** 返回规则集整体根 ID。 */
    public String getRuleSetId() {
        return ruleSetId;
    }

    /** 设置规则集整体根 ID。 */
    public void setRuleSetId(String ruleSetId) {
        this.ruleSetId = ruleSetId;
    }

    /** 返回所属决策点编码。 */
    public String getPointCode() {
        return pointCode;
    }

    /** 设置所属决策点编码。 */
    public void setPointCode(String pointCode) {
        this.pointCode = pointCode;
    }

    /** 返回规则集永久编码。 */
    public String getRuleSetCode() {
        return ruleSetCode;
    }

    /** 设置规则集永久编码。 */
    public void setRuleSetCode(String ruleSetCode) {
        this.ruleSetCode = ruleSetCode;
    }

    /** 返回该版本保存的规则集名称。 */
    public String getRuleSetName() {
        return ruleSetName;
    }

    /** 设置该版本保存的规则集名称。 */
    public void setRuleSetName(String ruleSetName) {
        this.ruleSetName = ruleSetName;
    }

    /** 返回精确规则版本 ID。 */
    public String getRuleSetVersionId() {
        return ruleSetVersionId;
    }

    /** 设置精确规则版本 ID。 */
    public void setRuleSetVersionId(String ruleSetVersionId) {
        this.ruleSetVersionId = ruleSetVersionId;
    }

    /** 返回规则业务版本号。 */
    public Integer getVersionNo() {
        return versionNo;
    }

    /** 设置规则业务版本号。 */
    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    /** 返回版本内规则编码。 */
    public String getRuleCode() {
        return ruleCode;
    }

    /** 设置版本内规则编码。 */
    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    /** 返回该版本保存的规则名称。 */
    public String getRuleName() {
        return ruleName;
    }

    /** 设置该版本保存的规则名称。 */
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    /** 返回引用字典类型。 */
    public String getReferenceKind() {
        return referenceKind;
    }

    /** 设置引用字典类型。 */
    public void setReferenceKind(String referenceKind) {
        this.referenceKind = referenceKind;
    }

    /** 返回标签持久化动作序号。 */
    public Integer getActionSequenceNo() {
        return actionSequenceNo;
    }

    /** 设置标签持久化动作序号。 */
    public void setActionSequenceNo(Integer actionSequenceNo) {
        this.actionSequenceNo = actionSequenceNo;
    }

    /** 返回标签工程主体绑定。 */
    public String getTargetBindingCode() {
        return targetBindingCode;
    }

    /** 设置标签工程主体绑定。 */
    public void setTargetBindingCode(String targetBindingCode) {
        this.targetBindingCode = targetBindingCode;
    }

    /** 返回规则集整体管理状态。 */
    public String getRuleSetStatus() {
        return ruleSetStatus;
    }

    /** 设置规则集整体管理状态。 */
    public void setRuleSetStatus(String ruleSetStatus) {
        this.ruleSetStatus = ruleSetStatus;
    }

    /** 返回保存版本是否为当前根指针。 */
    public boolean getIsCurrentConfiguration() {
        return isCurrentConfiguration;
    }

    /** 设置保存版本是否为当前根指针。 */
    public void setIsCurrentConfiguration(boolean isCurrentConfiguration) {
        this.isCurrentConfiguration = isCurrentConfiguration;
    }

    /** 返回精确规则版本的活动环境。 */
    public List<String> getActiveEnvironments() {
        return activeEnvironments;
    }

    /** 设置精确规则版本的活动环境。 */
    public void setActiveEnvironments(List<String> activeEnvironments) {
        this.activeEnvironments = activeEnvironments;
    }

    /** 返回原因来源。 */
    public String getSourceType() {
        return sourceType;
    }

    /** 设置原因来源。 */
    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    /** 返回流程整体根 ID。 */
    public String getFlowId() {
        return flowId;
    }

    /** 设置流程整体根 ID。 */
    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    /** 返回流程永久编码。 */
    public String getFlowCode() {
        return flowCode;
    }

    /** 设置流程永久编码。 */
    public void setFlowCode(String flowCode) {
        this.flowCode = flowCode;
    }

    /** 返回该版本保存的流程名称。 */
    public String getFlowName() {
        return flowName;
    }

    /** 设置该版本保存的流程名称。 */
    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }

    /** 返回精确流程版本 ID。 */
    public String getFlowVersionId() {
        return flowVersionId;
    }

    /** 设置精确流程版本 ID。 */
    public void setFlowVersionId(String flowVersionId) {
        this.flowVersionId = flowVersionId;
    }

    /** 返回流程业务版本号。 */
    public Integer getFlowVersionNo() {
        return flowVersionNo;
    }

    /** 设置流程业务版本号。 */
    public void setFlowVersionNo(Integer flowVersionNo) {
        this.flowVersionNo = flowVersionNo;
    }

    /** 返回保存时的稳定节点键。 */
    public String getNodeKey() {
        return nodeKey;
    }

    /** 设置保存时的稳定节点键。 */
    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    /** 返回保存时的节点名称。 */
    public String getNodeName() {
        return nodeName;
    }

    /** 设置保存时的节点名称。 */
    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    /** 返回流程整体管理状态。 */
    public String getFlowStatus() {
        return flowStatus;
    }

    /** 设置流程整体管理状态。 */
    public void setFlowStatus(String flowStatus) {
        this.flowStatus = flowStatus;
    }
}

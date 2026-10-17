package com.paytech.risk.decision.api.response;

import java.util.List;

/** 精确保存版本的字典引用，历史名称与当前发布关系分开表达。 */
public class SubjectTagReferenceResponseBody {

    /**
     * 规则集整体根 ID。
     * <li>正 BIGINT 字符串。</li>
     */
    private String ruleSetId;

    /** 所属决策点编码。 */
    private String pointCode;

    /** 规则集永久编码。 */
    private String ruleSetCode;

    /** 该版本保存的规则集名称。 */
    private String ruleSetName;

    /**
     * 精确规则版本 ID。
     * <li>正 BIGINT 字符串。</li>
     */
    private String ruleSetVersionId;

    /** 规则业务版本号。 */
    private int versionNo;

    /** 版本内规则编码。 */
    private String ruleCode;

    /** 该版本保存的规则名称。 */
    private String ruleName;

    /**
     * 引用字典类型。
     * <li>固定为 SUBJECT_TAG。</li>
     */
    private String referenceKind;

    /** 标签持久化动作序号。 */
    private Integer actionSequenceNo;

    /** 标签工程主体绑定。 */
    private String targetBindingCode;

    /** 规则集整体管理状态。 */
    private String ruleSetStatus;

    /**
     * 保存版本是否为当前根指针。
     * <li>删除根也可能为真。</li>
     */
    private boolean isCurrentConfiguration;

    /**
     * 精确规则版本的活动环境。
     * <li>按编码排序去重。</li>
     */
    private List<String> activeEnvironments;

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
    public int getVersionNo() {
        return versionNo;
    }

    /** 设置规则业务版本号。 */
    public void setVersionNo(int versionNo) {
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
}

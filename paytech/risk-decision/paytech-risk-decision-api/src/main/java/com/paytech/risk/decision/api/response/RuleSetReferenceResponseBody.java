package com.paytech.risk.api.decision.response;

import com.paytech.risk.api.decision.response.RuleSetDetailResponseBody;

/** 由真实已保存来源约束的规则只读结果，不能传任意规则版本读取已删除根。 */
public class RuleSetReferenceResponseBody {

    /** 真实引用来源类型，FLOW_VERSION 或 LEGACY_POINT。 */
    private String sourceType;

    /** 真实来源的准确版本 ID。 */
    private String sourceVersionId;

    /** 来源版本内的稳定节点键。 */
    private String nodeKey;

    /** 规则集当前管理状态，CONFIGURABLE 或 DELETED。 */
    private String ruleSetStatus;

    /** 实际绑定的准确规则版本详情。 */
    private RuleSetDetailResponseBody ruleSet;

    /** 供 JSON 属性绑定使用。 */
    public RuleSetReferenceResponseBody() { }

    /** 根据完整查询事实构造响应。 */
    public RuleSetReferenceResponseBody(String sourceType, String sourceVersionId, String nodeKey, String ruleSetStatus, RuleSetDetailResponseBody ruleSet) {
        this.sourceType = sourceType;
        this.sourceVersionId = sourceVersionId;
        this.nodeKey = nodeKey;
        this.ruleSetStatus = ruleSetStatus;
        this.ruleSet = ruleSet;
    }

    /** 返回真实引用来源类型，FLOW_VERSION 或 LEGACY_POINT。 */
    public String getSourceType() { return sourceType; }

    /** 设置真实引用来源类型，FLOW_VERSION 或 LEGACY_POINT。 */
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    /** 返回真实来源的准确版本 ID。 */
    public String getSourceVersionId() { return sourceVersionId; }

    /** 设置真实来源的准确版本 ID。 */
    public void setSourceVersionId(String sourceVersionId) { this.sourceVersionId = sourceVersionId; }

    /** 返回来源版本内的稳定节点键。 */
    public String getNodeKey() { return nodeKey; }

    /** 设置来源版本内的稳定节点键。 */
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }

    /** 返回规则集当前管理状态，CONFIGURABLE 或 DELETED。 */
    public String getRuleSetStatus() { return ruleSetStatus; }

    /** 设置规则集当前管理状态，CONFIGURABLE 或 DELETED。 */
    public void setRuleSetStatus(String ruleSetStatus) { this.ruleSetStatus = ruleSetStatus; }

    /** 返回实际绑定的准确规则版本详情。 */
    public RuleSetDetailResponseBody getRuleSet() { return ruleSet; }

    /** 设置实际绑定的准确规则版本详情。 */
    public void setRuleSet(RuleSetDetailResponseBody ruleSet) { this.ruleSet = ruleSet; }
}

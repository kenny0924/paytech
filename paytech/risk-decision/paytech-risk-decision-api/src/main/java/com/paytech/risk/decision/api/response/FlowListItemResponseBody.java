package com.paytech.risk.decision.api.response;

/** 每个流程根一行；当前版本数不是 API 版本。 */
public class FlowListItemResponseBody {

    /** 流程管理根 ID，以十进制字符串传输。 */
    private String flowId;

    /** 决策点编码。 */
    private String pointCode;

    /** 决策点内永久唯一的流程编码。 */
    private String flowCode;

    /** 所选版本保存时的流程名称。 */
    private String flowName;

    /** 当前配置版本 ID，以十进制字符串传输。 */
    private String currentFlowVersionId;

    /** 当前最高业务版本号。 */
    private int currentVersionNo;

    /** 管理根修订号，以十进制字符串传输。 */
    private String version;

    /** 当前版本的规则集节点数。 */
    private int ruleSetNodeCount;

    /** 当前版本保存人的 Account ID。 */
    private String updatedBy;

    /** 管理根最后更新时间，UTC 微秒。 */
    private String updateTime;

    /** 供 JSON 属性绑定使用。 */
    public FlowListItemResponseBody() { }

    /** 根据完整查询事实构造响应。 */
    public FlowListItemResponseBody(String flowId, String pointCode, String flowCode, String flowName, String currentFlowVersionId, int currentVersionNo, String version, int ruleSetNodeCount, String updatedBy, String updateTime) {
        this.flowId = flowId;
        this.pointCode = pointCode;
        this.flowCode = flowCode;
        this.flowName = flowName;
        this.currentFlowVersionId = currentFlowVersionId;
        this.currentVersionNo = currentVersionNo;
        this.version = version;
        this.ruleSetNodeCount = ruleSetNodeCount;
        this.updatedBy = updatedBy;
        this.updateTime = updateTime;
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

    /** 返回所选版本保存时的流程名称。 */
    public String getFlowName() { return flowName; }

    /** 设置所选版本保存时的流程名称。 */
    public void setFlowName(String flowName) { this.flowName = flowName; }

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

    /** 返回当前版本的规则集节点数。 */
    public int getRuleSetNodeCount() { return ruleSetNodeCount; }

    /** 设置当前版本的规则集节点数。 */
    public void setRuleSetNodeCount(int ruleSetNodeCount) { this.ruleSetNodeCount = ruleSetNodeCount; }

    /** 返回当前版本保存人的 Account ID。 */
    public String getUpdatedBy() { return updatedBy; }

    /** 设置当前版本保存人的 Account ID。 */
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    /** 返回管理根最后更新时间，UTC 微秒。 */
    public String getUpdateTime() { return updateTime; }

    /** 设置管理根最后更新时间，UTC 微秒。 */
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
}

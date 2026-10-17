package com.paytech.risk.decision.api.response;

/** 不可变版本历史条目。 */
public class FlowHistoryItemResponseBody {

    /** 所选准确流程版本 ID，以十进制字符串传输。 */
    private String flowVersionId;

    /** 所选版本的业务版本号。 */
    private int versionNo;

    /** 所选版本保存时的流程名称。 */
    private String flowName;

    /** 所选版本保存人的 Account ID。 */
    private String createdBy;

    /** 所选版本的保存时间，UTC 微秒。 */
    private String createTime;

    /** 所选版本保存时填写的变更原因。 */
    private String changeReason;

    /** 供 JSON 属性绑定使用。 */
    public FlowHistoryItemResponseBody() { }

    /** 根据完整查询事实构造响应。 */
    public FlowHistoryItemResponseBody(String flowVersionId, int versionNo, String flowName, String createdBy, String createTime, String changeReason) {
        this.flowVersionId = flowVersionId;
        this.versionNo = versionNo;
        this.flowName = flowName;
        this.createdBy = createdBy;
        this.createTime = createTime;
        this.changeReason = changeReason;
    }

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
}

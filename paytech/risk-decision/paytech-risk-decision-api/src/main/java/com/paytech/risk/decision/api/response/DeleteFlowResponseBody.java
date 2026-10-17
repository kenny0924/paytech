package com.paytech.risk.api.decision.response;

/** 本次整体删除结果，保留永久编码和配置历史。 */
public class DeleteFlowResponseBody {

    /**
     * 流程管理 ID。
     * <li>正 BIGINT 十进制字符串。</li>
     */
    private String flowId;

    /**
     * 流程整体管理状态。
     * <li>删除响应固定 DELETED。</li>
     */
    private String status;

    /**
     * 本次提交后的根修订号。
     * <li>正 BIGINT 十进制字符串。</li>
     */
    private String version;

    /**
     * 可信删除人的 Account ID。
     * <li>正 BIGINT 十进制字符串。</li>
     */
    private String deletedBy;

    /**
     * 整体删除时间。
     * <li>UTC RFC 3339，固定六位小数。</li>
     */
    private String deletedTime;

    /** 返回流程管理 ID。 */
    public String getFlowId() { return flowId; }

    /** 设置流程管理 ID。 */
    public void setFlowId(String flowId) { this.flowId = flowId; }

    /** 返回流程整体管理状态。 */
    public String getStatus() { return status; }

    /** 设置流程整体管理状态。 */
    public void setStatus(String status) { this.status = status; }

    /** 返回本次提交后的根修订号。 */
    public String getVersion() { return version; }

    /** 设置本次提交后的根修订号。 */
    public void setVersion(String version) { this.version = version; }

    /** 返回可信删除人的 Account ID。 */
    public String getDeletedBy() { return deletedBy; }

    /** 设置可信删除人的 Account ID。 */
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

    /** 返回整体删除时间。 */
    public String getDeletedTime() { return deletedTime; }

    /** 设置整体删除时间。 */
    public void setDeletedTime(String deletedTime) { this.deletedTime = deletedTime; }
}

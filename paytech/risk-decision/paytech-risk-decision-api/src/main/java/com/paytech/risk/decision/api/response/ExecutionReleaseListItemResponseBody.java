package com.paytech.risk.api.decision.response;

/** 执行发布列表项：发布、所冻结的决策流版本及激活信息；不含静态校验与影子汇总，见发布详情。 */
public class ExecutionReleaseListItemResponseBody {

    /** 发布 ID，正整数十进制字符串。 */
    private String releaseId;

    /** 发布编号。 */
    private String releaseNo;

    /** 决策点编码。 */
    private String pointCode;

    /** 环境 Code。 */
    private String environment;

    /** 状态 Code：DRAFT、VALIDATING、SHADOWING、REJECTED、ACTIVE、SUPERSEDED；激活事务内的 REVIEW_PENDING、APPROVED 一般不可见，调用方应容错未知值。 */
    private String status;

    /** 是否当前生效。 */
    private boolean active;

    /** 决策流 ID。 */
    private String flowId;

    /** 决策流编码。 */
    private String flowCode;

    /** 冻结的决策流版本 ID；回滚时以它新建执行发布。 */
    private String flowVersionId;

    /** 决策流版本号。 */
    private int flowVersionNo;

    /** 发布内容摘要。 */
    private String contentHash;

    /** 创建人 Account ID。 */
    private String createdBy;

    /** 创建时间，UTC ISO-8601。 */
    private String createTime;

    /** 激活人 Account ID；未激活为 null。 */
    private String activatedBy;

    /** 激活时间；未激活为 null。 */
    private String activatedTime;

    /** 被替代时间；未替代为 null。 */
    private String supersededTime;

    /** 读取发布 ID。 */
    public String getReleaseId() { return releaseId; }

    /** 设置发布 ID。 */
    public void setReleaseId(String releaseId) { this.releaseId = releaseId; }

    /** 读取发布编号。 */
    public String getReleaseNo() { return releaseNo; }

    /** 设置发布编号。 */
    public void setReleaseNo(String releaseNo) { this.releaseNo = releaseNo; }

    /** 读取决策点编码。 */
    public String getPointCode() { return pointCode; }

    /** 设置决策点编码。 */
    public void setPointCode(String pointCode) { this.pointCode = pointCode; }

    /** 读取环境。 */
    public String getEnvironment() { return environment; }

    /** 设置环境。 */
    public void setEnvironment(String environment) { this.environment = environment; }

    /** 读取状态。 */
    public String getStatus() { return status; }

    /** 设置状态。 */
    public void setStatus(String status) { this.status = status; }

    /** 读取是否当前生效。 */
    public boolean isActive() { return active; }

    /** 设置是否当前生效。 */
    public void setActive(boolean active) { this.active = active; }

    /** 读取决策流 ID。 */
    public String getFlowId() { return flowId; }

    /** 设置决策流 ID。 */
    public void setFlowId(String flowId) { this.flowId = flowId; }

    /** 读取决策流编码。 */
    public String getFlowCode() { return flowCode; }

    /** 设置决策流编码。 */
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }

    /** 读取决策流版本 ID。 */
    public String getFlowVersionId() { return flowVersionId; }

    /** 设置决策流版本 ID。 */
    public void setFlowVersionId(String flowVersionId) { this.flowVersionId = flowVersionId; }

    /** 读取决策流版本号。 */
    public int getFlowVersionNo() { return flowVersionNo; }

    /** 设置决策流版本号。 */
    public void setFlowVersionNo(int flowVersionNo) { this.flowVersionNo = flowVersionNo; }

    /** 读取内容摘要。 */
    public String getContentHash() { return contentHash; }

    /** 设置内容摘要。 */
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    /** 读取创建人。 */
    public String getCreatedBy() { return createdBy; }

    /** 设置创建人。 */
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    /** 读取创建时间。 */
    public String getCreateTime() { return createTime; }

    /** 设置创建时间。 */
    public void setCreateTime(String createTime) { this.createTime = createTime; }

    /** 读取激活人。 */
    public String getActivatedBy() { return activatedBy; }

    /** 设置激活人。 */
    public void setActivatedBy(String activatedBy) { this.activatedBy = activatedBy; }

    /** 读取激活时间。 */
    public String getActivatedTime() { return activatedTime; }

    /** 设置激活时间。 */
    public void setActivatedTime(String activatedTime) { this.activatedTime = activatedTime; }

    /** 读取被替代时间。 */
    public String getSupersededTime() { return supersededTime; }

    /** 设置被替代时间。 */
    public void setSupersededTime(String supersededTime) { this.supersededTime = supersededTime; }
}

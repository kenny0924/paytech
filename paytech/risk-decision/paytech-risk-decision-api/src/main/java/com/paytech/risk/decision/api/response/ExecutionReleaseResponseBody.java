package com.paytech.risk.decision.api.response;

import java.util.List;

/** 执行发布、静态校验与最近一次影子运行。 */
public class ExecutionReleaseResponseBody {

    /** 发布 ID，正整数十进制字符串。 */
    private String releaseId;

    /** 发布编号。 */
    private String releaseNo;

    /** 决策点编码。 */
    private String pointCode;

    /** 冻结的决策流版本 ID。 */
    private String flowVersionId;

    /** 环境 Code。 */
    private String environment;

    /**
     * 状态
     * <li>DRAFT、SHADOWING、REJECTED、ACTIVE 或 SUPERSEDED；REVIEW_PENDING、APPROVED 只在激活事务内短暂经过。</li>
     */
    private String status;

    /** 并发版本。 */
    private int version;

    /** 内容摘要。 */
    private String contentHash;

    /** 激活时间，UTC ISO-8601；未激活为空。 */
    private String activatedTime;

    /** 被替代时间，UTC ISO-8601；未被替代为空。 */
    private String supersededTime;

    /** 八项静态校验；尚未校验为空列表。 */
    private List<StaticCheck> checks;

    /** 最近一次影子运行；没有为空。 */
    private ShadowRun shadowRun;

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

    /** 读取冻结的决策流版本 ID。 */
    public String getFlowVersionId() { return flowVersionId; }

    /** 设置冻结的决策流版本 ID。 */
    public void setFlowVersionId(String flowVersionId) { this.flowVersionId = flowVersionId; }

    /** 读取环境 Code。 */
    public String getEnvironment() { return environment; }

    /** 设置环境 Code。 */
    public void setEnvironment(String environment) { this.environment = environment; }

    /** 读取状态。 */
    public String getStatus() { return status; }

    /** 设置状态。 */
    public void setStatus(String status) { this.status = status; }

    /** 读取并发版本。 */
    public int getVersion() { return version; }

    /** 设置并发版本。 */
    public void setVersion(int version) { this.version = version; }

    /** 读取内容摘要。 */
    public String getContentHash() { return contentHash; }

    /** 设置内容摘要。 */
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    /** 读取激活时间。 */
    public String getActivatedTime() { return activatedTime; }

    /** 设置激活时间。 */
    public void setActivatedTime(String activatedTime) { this.activatedTime = activatedTime; }

    /** 读取被替代时间。 */
    public String getSupersededTime() { return supersededTime; }

    /** 设置被替代时间。 */
    public void setSupersededTime(String supersededTime) { this.supersededTime = supersededTime; }

    /** 读取静态校验。 */
    public List<StaticCheck> getChecks() { return checks; }

    /** 设置静态校验。 */
    public void setChecks(List<StaticCheck> checks) { this.checks = checks; }

    /** 读取影子运行。 */
    public ShadowRun getShadowRun() { return shadowRun; }

    /** 设置影子运行。 */
    public void setShadowRun(ShadowRun shadowRun) { this.shadowRun = shadowRun; }
}

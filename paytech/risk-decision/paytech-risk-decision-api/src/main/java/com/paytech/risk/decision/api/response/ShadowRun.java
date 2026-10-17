package com.paytech.risk.decision.api.response;

/** 影子运行汇总。 */
public class ShadowRun {

    /** 批次 ID。 */
    private String shadowRunId;

    /** 基线发布 ID。 */
    private String baselineReleaseId;

    /** RUNNING、COMPLETED 或 FAILED。 */
    private String status;

    /** 目标样本数。 */
    private int sampleTarget;

    /** 实际样本数。 */
    private int sampleCount;

    /** 放行变审核的样本数；完成后非空。 */
    private Integer allowToReviewCount;

    /** 放行变拒绝的样本数；完成后非空。 */
    private Integer allowToDenyCount;

    /** 拒绝或审核变放行的样本数；完成后非空。 */
    private Integer denyToAllowCount;

    /** 认证要求变化的样本数；完成后非空。 */
    private Integer actionChangeCount;

    /** 存在无值 Feature 的样本数；完成后非空。 */
    private Integer featureMissingCount;

    /** 候选执行失败数；完成后非空。 */
    private Integer failedCount;

    /** 候选执行超时数；完成后非空。 */
    private Integer timedOutCount;

    /** 候选耗时 P95 毫秒；完成后非空。 */
    private Integer p95Millis;

    /** 候选耗时 P99 毫秒；完成后非空。 */
    private Integer p99Millis;

    /** 技术门槛：没有失败与超时为 PASSED；完成后非空。 */
    private String checkStatus;

    /** 读取批次 ID。 */
    public String getShadowRunId() { return shadowRunId; }

    /** 设置批次 ID。 */
    public void setShadowRunId(String shadowRunId) { this.shadowRunId = shadowRunId; }

    /** 读取基线发布 ID。 */
    public String getBaselineReleaseId() { return baselineReleaseId; }

    /** 设置基线发布 ID。 */
    public void setBaselineReleaseId(String baselineReleaseId) { this.baselineReleaseId = baselineReleaseId; }

    /** 读取状态。 */
    public String getStatus() { return status; }

    /** 设置状态。 */
    public void setStatus(String status) { this.status = status; }

    /** 读取目标样本数。 */
    public int getSampleTarget() { return sampleTarget; }

    /** 设置目标样本数。 */
    public void setSampleTarget(int sampleTarget) { this.sampleTarget = sampleTarget; }

    /** 读取实际样本数。 */
    public int getSampleCount() { return sampleCount; }

    /** 设置实际样本数。 */
    public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }

    /** 读取放行变审核数。 */
    public Integer getAllowToReviewCount() { return allowToReviewCount; }

    /** 设置放行变审核数。 */
    public void setAllowToReviewCount(Integer allowToReviewCount) { this.allowToReviewCount = allowToReviewCount; }

    /** 读取放行变拒绝数。 */
    public Integer getAllowToDenyCount() { return allowToDenyCount; }

    /** 设置放行变拒绝数。 */
    public void setAllowToDenyCount(Integer allowToDenyCount) { this.allowToDenyCount = allowToDenyCount; }

    /** 读取拒绝变放行数。 */
    public Integer getDenyToAllowCount() { return denyToAllowCount; }

    /** 设置拒绝变放行数。 */
    public void setDenyToAllowCount(Integer denyToAllowCount) { this.denyToAllowCount = denyToAllowCount; }

    /** 读取认证要求变化数。 */
    public Integer getActionChangeCount() { return actionChangeCount; }

    /** 设置认证要求变化数。 */
    public void setActionChangeCount(Integer actionChangeCount) { this.actionChangeCount = actionChangeCount; }

    /** 读取Feature 无值数。 */
    public Integer getFeatureMissingCount() { return featureMissingCount; }

    /** 设置Feature 无值数。 */
    public void setFeatureMissingCount(Integer featureMissingCount) { this.featureMissingCount = featureMissingCount; }

    /** 读取失败数。 */
    public Integer getFailedCount() { return failedCount; }

    /** 设置失败数。 */
    public void setFailedCount(Integer failedCount) { this.failedCount = failedCount; }

    /** 读取超时数。 */
    public Integer getTimedOutCount() { return timedOutCount; }

    /** 设置超时数。 */
    public void setTimedOutCount(Integer timedOutCount) { this.timedOutCount = timedOutCount; }

    /** 读取P95 耗时。 */
    public Integer getP95Millis() { return p95Millis; }

    /** 设置P95 耗时。 */
    public void setP95Millis(Integer p95Millis) { this.p95Millis = p95Millis; }

    /** 读取P99 耗时。 */
    public Integer getP99Millis() { return p99Millis; }

    /** 设置P99 耗时。 */
    public void setP99Millis(Integer p99Millis) { this.p99Millis = p99Millis; }

    /** 读取技术门槛。 */
    public String getCheckStatus() { return checkStatus; }

    /** 设置技术门槛。 */
    public void setCheckStatus(String checkStatus) { this.checkStatus = checkStatus; }
}

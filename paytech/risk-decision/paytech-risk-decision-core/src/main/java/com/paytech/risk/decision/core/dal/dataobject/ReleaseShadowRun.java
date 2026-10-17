package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.ShadowRunStatus;
import java.time.Instant;

import lombok.Data;

/** 候选发布回放基线评估的影子批次（t_release_shadow_run）。 */
@Data
public class ReleaseShadowRun {
    /** 批次 ID。 */
    private long shadowRunId;

    /** 批次编号，等于批次 ID 十进制文本。 */
    private String runNo;

    /** 基线发布 ID，即当前 ACTIVE 发布。 */
    private long baselineReleaseId;

    /** 候选发布 ID。 */
    private long candidateReleaseId;

    /** 批次状态。 */
    private ShadowRunStatus status;

    /** 目标样本数。 */
    private int sampleTarget;

    /** 实际回放样本数，不超过目标。 */
    private int sampleCount;

    /** 开始 UTC 时间。 */
    private Instant startedTime;

    /** 完成 UTC 时间。 */
    private Instant completedTime;

    /** 基线放行、候选审核的样本数。 */
    private Integer allowToReviewCount;

    /** 基线放行、候选拒绝的样本数。 */
    private Integer allowToDenyCount;

    /** 基线拒绝或审核、候选放行的样本数。 */
    private Integer denyToAllowCount;

    /** 认证要求变化的样本数。 */
    private Integer actionChangeCount;

    /** Feature 无值的样本数。 */
    private Integer featureMissingCount;

    /** 候选执行失败的样本数。 */
    private Integer failedCount;

    /** 候选执行超时的样本数。 */
    private Integer timedOutCount;

    /** 候选执行耗时 P95。 */
    private Integer p95Millis;

    /** 候选执行耗时 P99。 */
    private Integer p99Millis;

    /** 技术门槛结论，PASSED 或 FAILED；完成后非空。 */
    private String checkStatus;

    /** 并发版本。 */
    private long version;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}

package com.paytech.risk.decision.domain.release;

import java.time.Instant;
import java.util.List;

import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;

/**
 * 执行发布的查询结果：发布、静态校验与最近一次影子运行
 *
 * @param releaseId 发布 ID
 * @param releaseNo 发布编号
 * @param pointCode 决策点编码
 * @param flowVersionId 决策流版本 ID
 * @param environment 环境 Code
 * @param status 状态 Code
 * @param version 并发版本
 * @param contentHash 内容摘要
 * @param activatedTime 激活时间
 * @param supersededTime 被替代时间
 * @param checks 八项静态校验；尚未校验为空列表
 * @param shadowRun 最近一次影子运行；没有为空
 */
public record ExecutionReleaseView(long releaseId, String releaseNo, String pointCode, long flowVersionId,
    String environment, String status, int version, String contentHash, Instant activatedTime, Instant supersededTime,
    List<StaticCheckResult> checks, ShadowRunSummary shadowRun) {

    /**
     * 影子运行汇总
     *
     * @param shadowRunId 批次 ID
     * @param baselineReleaseId 基线发布 ID
     * @param status 状态 Code
     * @param sampleTarget 目标样本数
     * @param sampleCount 实际样本数
     * @param allowToReviewCount 放行变审核
     * @param allowToDenyCount 放行变拒绝
     * @param denyToAllowCount 拒绝变放行（审核变放行不计入）
     * @param actionChangeCount 认证要求变化
     * @param featureMissingCount Feature 无值
     * @param failedCount 候选执行失败
     * @param timedOutCount 候选执行超时
     * @param p95Millis 耗时 P95
     * @param p99Millis 耗时 P99
     * @param checkStatus 技术门槛结论
     */
    public record ShadowRunSummary(long shadowRunId, long baselineReleaseId, String status, int sampleTarget,
        int sampleCount, Integer allowToReviewCount, Integer allowToDenyCount, Integer denyToAllowCount,
        Integer actionChangeCount, Integer featureMissingCount, Integer failedCount, Integer timedOutCount,
        Integer p95Millis, Integer p99Millis, String checkStatus) { }
}

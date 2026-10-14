package com.paytech.risk.decision.domain.release;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseShadowRun;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.ShadowRunStatus;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;

/**
 * 影子样本的比较与批次汇总；纯内存计算
 * <li>严重程度：决定不变为 NONE；放行与拒绝互换为 HIGH；其余决定变化或认证要求变化为 MEDIUM；候选技术失败为 CRITICAL。</li>
 * <li>技术门槛：候选没有失败与超时为 PASSED；决定变化不设自动阈值，由激活人判断。</li>
 */
public final class ShadowRunSummaries {
    /** 纯函数工具。 */
    private ShadowRunSummaries() { }

    /**
     * 样本比较严重程度
     *
     * @param baseline 基线决定
     * @param candidate 候选决定；技术失败为空
     * @param actionChanged 认证要求是否变化
     * @return 严重程度 Code
     */
    public static String severity(DecisionValue baseline, DecisionValue candidate, boolean actionChanged) {
        if (candidate == null) {
            return "CRITICAL";
        }
        if (baseline == candidate) {
            return actionChanged ? "MEDIUM" : "NONE";
        }
        boolean swapped = baseline == DecisionValue.ALLOW && candidate == DecisionValue.DENY
            || baseline == DecisionValue.DENY && candidate == DecisionValue.ALLOW;
        return swapped ? "HIGH" : "MEDIUM";
    }

    /**
     * 写入批次汇总
     *
     * @param run 运行中的批次，原地写入汇总字段
     * @param samples 本批次全部影子评估
     * @param baselines 样本对应的基线评估，按评估 ID 索引
     * @param featureMissing 存在无值 Feature 的样本数
     * @param time 完成时间
     */
    public static void complete(ReleaseShadowRun run, List<DecisionEvaluation> samples,
        Map<Long, DecisionEvaluation> baselines, int featureMissing, Instant time) {
        int allowToReview = 0;
        int allowToDeny = 0;
        int denyToAllow = 0;
        for (DecisionEvaluation sample : samples) {
            DecisionValue before = baselines.get(sample.getBaselineEvaluationId()).getBusinessDecision();
            DecisionValue after = sample.getBusinessDecision();
            allowToReview += before == DecisionValue.ALLOW && after == DecisionValue.REVIEW ? 1 : 0;
            allowToDeny += before == DecisionValue.ALLOW && after == DecisionValue.DENY ? 1 : 0;
            denyToAllow += before != DecisionValue.ALLOW && after == DecisionValue.ALLOW ? 1 : 0;
        }
        int failed = count(samples, TechnicalStatus.FAILED);
        int timedOut = count(samples, TechnicalStatus.TIMED_OUT);
        run.setStatus(ShadowRunStatus.COMPLETED);
        run.setSampleCount(samples.size());
        run.setCompletedTime(time);
        run.setAllowToReviewCount(allowToReview);
        run.setAllowToDenyCount(allowToDeny);
        run.setDenyToAllowCount(denyToAllow);
        run.setActionChangeCount((int)samples.stream().filter(s -> Boolean.TRUE.equals(s.getActionChangedFlag())).count());
        run.setFeatureMissingCount(featureMissing);
        run.setFailedCount(failed);
        run.setTimedOutCount(timedOut);
        List<Integer> elapsed = samples.stream().map(DecisionEvaluation::getElapsedMillis).sorted().toList();
        run.setP95Millis(percentile(elapsed, 95));
        run.setP99Millis(percentile(elapsed, 99));
        run.setCheckStatus(failed == 0 && timedOut == 0 ? "PASSED" : "FAILED");
        run.setUpdateTime(time);
    }

    /** 技术状态计数。 */
    static int count(List<DecisionEvaluation> samples, TechnicalStatus status) {
        return (int)samples.stream().filter(sample -> sample.getTechnicalStatus() == status).count();
    }

    /** 最近秩百分位；没有样本为 0。 */
    static int percentile(List<Integer> sorted, int percent) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int rank = (int)Math.ceil(percent / 100.0 * sorted.size());
        return Objects.requireNonNull(sorted.get(Math.max(rank, 1) - 1));
    }
}

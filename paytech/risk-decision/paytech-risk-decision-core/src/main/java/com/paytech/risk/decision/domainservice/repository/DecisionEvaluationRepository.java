package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import java.util.List;
import java.util.Optional;

/** 决策评估的单表持久化边界。 */
public interface DecisionEvaluationRepository {
    /**
     * 插入评估
     * <li>评估请求身份或影子样本重复时抛出并发冲突。</li>
     */
    void insert(DecisionEvaluation row);

    /** 按 ID 读取。 */
    Optional<DecisionEvaluation> findById(long evaluationId);

    /** 读取某发布最近的成功正式评估，新评估在前。 */
    List<DecisionEvaluation> findRecentPrimary(long releaseId, int limit);

    /** 读取一个影子批次的全部样本评估。 */
    List<DecisionEvaluation> findByShadowRun(long shadowRunId);

    /** 读取以某正式评估为基线的影子评估，新评估在前，最多 limit 条。 */
    List<DecisionEvaluation> findByBaseline(long baselineEvaluationId, int limit);
}

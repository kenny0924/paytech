package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.TransactionRiskAssessment;
import java.util.Optional;

/** 交易风险评估快照的单表持久化边界。 */
public interface TransactionRiskAssessmentRepository {
    /**
     * 插入快照
     * <li>同一支付尝试已有评估时抛出并发冲突，调用方回读原评估。</li>
     */
    void insert(TransactionRiskAssessment row);

    /** 按支付尝试读取。 */
    Optional<TransactionRiskAssessment> findByAttempt(long paymentAttemptId);

    /** 按正式评估读取。 */
    Optional<TransactionRiskAssessment> findByEvaluation(long evaluationId);
}

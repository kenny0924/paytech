package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.EvaluationFeatureResult;
import java.util.List;

/** 评估 Feature 结果的单表持久化边界。 */
public interface EvaluationFeatureResultRepository {
    /** 批量插入；空集合不访问数据库。 */
    void insertAll(List<EvaluationFeatureResult> rows);

    /** 读取一次评估的 Feature 结果。 */
    List<EvaluationFeatureResult> findByEvaluation(long evaluationId);
}

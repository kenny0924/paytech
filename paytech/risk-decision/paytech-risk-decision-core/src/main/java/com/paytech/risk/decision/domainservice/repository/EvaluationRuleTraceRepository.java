package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.EvaluationRuleTrace;
import java.util.List;

/** 评估规则轨迹的单表持久化边界。 */
public interface EvaluationRuleTraceRepository {
    /** 批量插入；空集合不访问数据库。 */
    void insertAll(List<EvaluationRuleTrace> rows);

    /** 读取一次评估的轨迹。 */
    List<EvaluationRuleTrace> findByEvaluation(long evaluationId);
}

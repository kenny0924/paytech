package com.paytech.risk.decision.domainservice.repository.impl;

import com.paytech.risk.decision.core.dal.dataobject.EvaluationRuleTrace;
import com.paytech.risk.decision.core.dal.mapper.EvaluationRuleTraceMapper;
import com.paytech.risk.decision.domainservice.repository.EvaluationRuleTraceRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

/** 评估规则轨迹持久化。 */
@Repository
@AllArgsConstructor
public class EvaluationRuleTraceRepositoryImpl implements EvaluationRuleTraceRepository {
    private final EvaluationRuleTraceMapper mapper;

    /** 批量插入；空集合不访问数据库。 */
    @Override
    public void insertAll(List<EvaluationRuleTrace> rows) {
        if (!rows.isEmpty()) {
            mapper.insertBatch(rows);
        }
    }

    /** 读取一次评估的轨迹。 */
    @Override
    public List<EvaluationRuleTrace> findByEvaluation(long evaluationId) {
        return mapper.selectByEvaluation(evaluationId);
    }
}

package com.paytech.risk.decision.domainservice.repository.impl;

import com.paytech.risk.decision.core.dal.dataobject.EvaluationFeatureResult;
import com.paytech.risk.decision.core.dal.mapper.EvaluationFeatureResultMapper;
import com.paytech.risk.decision.domainservice.repository.EvaluationFeatureResultRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

/** 评估 Feature 结果持久化。 */
@Repository
@AllArgsConstructor
public class EvaluationFeatureResultRepositoryImpl implements EvaluationFeatureResultRepository {
    private final EvaluationFeatureResultMapper mapper;

    /** 批量插入；空集合不访问数据库。 */
    @Override
    public void insertAll(List<EvaluationFeatureResult> rows) {
        if (!rows.isEmpty()) {
            mapper.insertBatch(rows);
        }
    }

    /** 读取一次评估的 Feature 结果。 */
    @Override
    public List<EvaluationFeatureResult> findByEvaluation(long evaluationId) {
        return mapper.selectByEvaluation(evaluationId);
    }
}

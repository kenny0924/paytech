package com.paytech.risk.decision.domainservice.repository.impl;

import com.paytech.risk.decision.core.dal.dataobject.EvaluationFlowTrace;
import com.paytech.risk.decision.core.dal.mapper.EvaluationFlowTraceMapper;
import com.paytech.risk.decision.domainservice.repository.EvaluationFlowTraceRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

/** 评估流程轨迹持久化。 */
@Repository
@AllArgsConstructor
public class EvaluationFlowTraceRepositoryImpl implements EvaluationFlowTraceRepository {
    private final EvaluationFlowTraceMapper mapper;

    /** 批量插入；空集合不访问数据库。 */
    @Override
    public void insertAll(List<EvaluationFlowTrace> rows) {
        if (!rows.isEmpty()) {
            mapper.insertBatch(rows);
        }
    }

    /** 按经过顺序读取一次评估的流程轨迹。 */
    @Override
    public List<EvaluationFlowTrace> findByEvaluation(long evaluationId) {
        return mapper.selectByEvaluation(evaluationId);
    }
}

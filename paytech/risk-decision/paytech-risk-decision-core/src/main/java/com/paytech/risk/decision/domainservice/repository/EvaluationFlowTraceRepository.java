package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.EvaluationFlowTrace;
import java.util.List;

/** 评估流程轨迹的单表持久化边界。 */
public interface EvaluationFlowTraceRepository {
    /** 批量插入；空集合不访问数据库。 */
    void insertAll(List<EvaluationFlowTrace> rows);

    /** 按经过顺序读取一次评估的流程轨迹；本需求上线前的评估没有记录。 */
    List<EvaluationFlowTrace> findByEvaluation(long evaluationId);
}

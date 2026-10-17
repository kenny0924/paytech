package com.paytech.risk.decision.core.dal.mapper;

import com.paytech.risk.decision.core.dal.dataobject.EvaluationFlowTrace;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 评估经过的决策流节点表映射。 */
public interface EvaluationFlowTraceMapper {
    /** 批量插入；空集合由调用方跳过。 */
    int insertBatch(@Param("rows") List<EvaluationFlowTrace> rows);

    /** 按经过顺序读取一次评估的流程轨迹。 */
    List<EvaluationFlowTrace> selectByEvaluation(@Param("evaluationId") long evaluationId);
}

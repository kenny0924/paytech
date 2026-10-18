package com.paytech.risk.decision.core.dal.mapper;

import com.paytech.risk.decision.core.dal.dataobject.EvaluationRuleTrace;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 规则与条件谓词的可解释轨迹表映射。 */
public interface EvaluationRuleTraceMapper {
    /** 插入一行。 */
    int insert(@Param("row") EvaluationRuleTrace row);

    /** 批量插入；空集合由调用方跳过。 */
    int insertBatch(@Param("rows") List<EvaluationRuleTrace> rows);

    /** 读取一次评估的轨迹。 */
    List<EvaluationRuleTrace> selectByEvaluation(@Param("evaluationId") long evaluationId);
}

package com.paytech.risk.decision.core.dal.mapper;

import com.paytech.risk.decision.core.dal.dataobject.EvaluationFeatureResult;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 评估中实际执行的 Feature 结果表映射。 */
public interface EvaluationFeatureResultMapper {
    /** 插入一行。 */
    int insert(@Param("row") EvaluationFeatureResult row);

    /** 批量插入；空集合由调用方跳过。 */
    int insertBatch(@Param("rows") List<EvaluationFeatureResult> rows);

    /** 读取一次评估的 Feature 结果。 */
    List<EvaluationFeatureResult> selectByEvaluation(@Param("evaluationId") long evaluationId);
}

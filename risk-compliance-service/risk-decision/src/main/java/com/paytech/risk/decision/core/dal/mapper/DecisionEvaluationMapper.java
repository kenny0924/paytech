package com.paytech.risk.decision.core.dal.mapper;

import com.paytech.risk.decision.core.dal.dataobject.DecisionEvaluation;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 一次正式、影子或重放评估的执行事实表映射。 */
public interface DecisionEvaluationMapper {
    /** 插入一行。 */
    int insert(@Param("row") DecisionEvaluation row);

    /** 按 ID 读取。 */
    DecisionEvaluation selectById(@Param("evaluationId") long evaluationId);

    /** 读取某发布最近的成功正式评估，供影子回放取样。 */
    List<DecisionEvaluation> selectRecentPrimary(@Param("releaseId") long releaseId, @Param("limit") int limit);

    /** 读取一个影子批次的全部样本评估。 */
    List<DecisionEvaluation> selectByShadowRun(@Param("shadowRunId") long shadowRunId);
}

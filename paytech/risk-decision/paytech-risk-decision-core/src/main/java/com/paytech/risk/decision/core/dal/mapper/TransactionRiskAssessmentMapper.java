package com.paytech.risk.decision.core.dal.mapper;

import com.paytech.risk.decision.core.dal.dataobject.TransactionRiskAssessment;
import org.apache.ibatis.annotations.Param;

/** 支付尝试风控评估的不可变输入快照表映射。 */
public interface TransactionRiskAssessmentMapper {
    /** 插入一行。 */
    int insert(@Param("row") TransactionRiskAssessment row);

    /** 按支付尝试读取。 */
    TransactionRiskAssessment selectByAttempt(@Param("paymentAttemptId") long paymentAttemptId);

    /** 按评估读取。 */
    TransactionRiskAssessment selectByEvaluation(@Param("evaluationId") long evaluationId);
}

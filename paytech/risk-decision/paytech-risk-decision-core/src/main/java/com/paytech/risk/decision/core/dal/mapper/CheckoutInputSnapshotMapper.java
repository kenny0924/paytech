package com.paytech.risk.decision.core.dal.mapper;

import com.paytech.risk.decision.core.dal.dataobject.CheckoutInputSnapshot;
import org.apache.ibatis.annotations.Param;

/** 结账风控评估的不可变输入快照表映射。 */
public interface CheckoutInputSnapshotMapper {
    /** 插入一行。 */
    int insert(@Param("row") CheckoutInputSnapshot row);

    /** 按订单读取。 */
    CheckoutInputSnapshot selectByOrder(@Param("orderId") long orderId);

    /** 按评估读取。 */
    CheckoutInputSnapshot selectByEvaluation(@Param("evaluationId") long evaluationId);
}

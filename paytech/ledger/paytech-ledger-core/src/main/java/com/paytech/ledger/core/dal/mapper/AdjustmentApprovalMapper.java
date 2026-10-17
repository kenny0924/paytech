package com.paytech.ledger.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.AdjustmentApproval;

/** t_adjustment_approval：人工调整审批记录持久化（只追加）。 */
@Mapper
public interface AdjustmentApprovalMapper {

    /** 插入审批记录。 */
    int insert(AdjustmentApproval record);

    /** 读取一笔调整的全部审批记录。 */
    List<AdjustmentApproval> selectByAdjustment(@Param("adjustmentId") long adjustmentId);
}

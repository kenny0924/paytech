package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.AdjustmentApproval;

/** t_AdjustmentApproval：人工调整审批记录追加与读取。 */
public interface AdjustmentApprovalRepository {

    /** 插入审批记录；同一角色已有记录时按并发冲突拒绝。 */
    void insert(AdjustmentApproval approval);

    /** 读取一笔调整的全部审批记录。 */
    List<AdjustmentApproval> findByAdjustment(long adjustmentId);
}

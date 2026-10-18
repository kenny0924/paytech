package com.paytech.ledger.domainservice.repository;

import com.paytech.ledger.core.dal.dataobject.ManualAdjustment;
import com.paytech.ledger.core.enums.AdjustmentStatus;

/** t_ManualAdjustment：人工调整读写。 */
public interface ManualAdjustmentRepository {

    /** 插入人工调整头。 */
    void insert(ManualAdjustment adjustment);

    /** 按主键读取调整，不存在返回空。 */
    ManualAdjustment findById(long id);

    /** 按原状态与版本迁移调整状态，成功后同步原对象。 */
    void updateStatus(ManualAdjustment adjustment, AdjustmentStatus status);
}

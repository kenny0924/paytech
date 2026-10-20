package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.ManualAdjustment;
import com.paytech.ledger.core.enums.AdjustmentStatus;
import com.paytech.ledger.domain.ManualAdjustmentQuery;
import com.paytech.ledger.domain.view.ManualAdjustmentView;

/** t_ManualAdjustment：人工调整读写。 */
public interface ManualAdjustmentRepository {

    /** 插入人工调整头。 */
    void insert(ManualAdjustment adjustment);

    /** 按主键读取调整，不存在返回空。 */
    ManualAdjustment findById(long id);

    /** 按原状态与版本迁移调整状态，成功后同步原对象。 */
    void updateStatus(ManualAdjustment adjustment, AdjustmentStatus status);

    /** 按条件分页读取调整摘要（含过账分录主键），按提交时间、主键降序。 */
    List<ManualAdjustmentView> findViewPage(ManualAdjustmentQuery query);

    /** 统计符合条件的调整数。 */
    long countPage(ManualAdjustmentQuery query);
}

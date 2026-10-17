package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.ManualAdjustmentLine;

/** t_ManualAdjustmentLine：人工调整明细读写。 */
public interface ManualAdjustmentLineRepository {

    /** 批量插入调整明细。 */
    void insertBatch(List<ManualAdjustmentLine> lines);

    /** 按行号升序读取一笔调整的明细。 */
    List<ManualAdjustmentLine> findByAdjustment(long adjustmentId);
}

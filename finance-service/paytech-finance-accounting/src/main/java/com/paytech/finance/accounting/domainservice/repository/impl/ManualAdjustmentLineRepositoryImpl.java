package com.paytech.finance.accounting.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.ManualAdjustmentLine;
import com.paytech.finance.accounting.core.dal.mapper.ManualAdjustmentLineMapper;
import com.paytech.finance.accounting.domainservice.repository.ManualAdjustmentLineRepository;

import lombok.AllArgsConstructor;

/** ManualAdjustmentLineRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class ManualAdjustmentLineRepositoryImpl implements ManualAdjustmentLineRepository {
    private final ManualAdjustmentLineMapper mapper;

    /** 批量插入调整明细。 */
    @Override
    public void insertBatch(List<ManualAdjustmentLine> lines) {
        mapper.insertBatch(lines);
    }

    /** 按行号升序读取一笔调整的明细。 */
    @Override
    public List<ManualAdjustmentLine> findByAdjustment(long adjustmentId) {
        return mapper.selectByAdjustment(adjustmentId);
    }
}

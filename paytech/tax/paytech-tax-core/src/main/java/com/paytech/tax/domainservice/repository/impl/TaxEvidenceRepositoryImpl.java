package com.paytech.tax.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.tax.core.dal.dataobject.TaxEvidence;
import com.paytech.tax.core.dal.mapper.TaxEvidenceMapper;
import com.paytech.tax.domainservice.repository.TaxEvidenceRepository;

import lombok.AllArgsConstructor;

/** TaxEvidenceRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TaxEvidenceRepositoryImpl implements TaxEvidenceRepository {
    private final TaxEvidenceMapper mapper;

    /** 一次插入全部行；空列表不发出 SQL，行数不符为系统异常。 */
    @Override
    public void insertAll(List<TaxEvidence> rows) {
        if (rows.isEmpty()) {
            return;
        }
        if (mapper.batchInsert(rows) != rows.size()) {
            throw new ErrorException("t_tax_evidence 批量插入行数不符: 期望 " + rows.size());
        }
    }

    /** 一组计算的全部证据。 */
    @Override
    public List<TaxEvidence> findByCalculationIds(Collection<Long> calculationIds) {
        if (calculationIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectByCalculationIds(calculationIds);
    }
}

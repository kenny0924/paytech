package com.paytech.tax.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.tax.core.dal.dataobject.TaxCalculationLine;
import com.paytech.tax.core.dal.mapper.TaxCalculationLineMapper;
import com.paytech.tax.domainservice.repository.TaxCalculationLineRepository;

import lombok.AllArgsConstructor;

/** TaxCalculationLineRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TaxCalculationLineRepositoryImpl implements TaxCalculationLineRepository {
    private final TaxCalculationLineMapper mapper;

    /** 一次插入全部行；空列表不发出 SQL，行数不符为系统异常。 */
    @Override
    public void insertAll(List<TaxCalculationLine> rows) {
        if (rows.isEmpty()) {
            return;
        }
        if (mapper.batchInsert(rows) != rows.size()) {
            throw new ErrorException("t_tax_calculation_line 批量插入行数不符: 期望 " + rows.size());
        }
    }

    /** 一组计算的全部行，按计算与行号升序。 */
    @Override
    public List<TaxCalculationLine> findByCalculationIds(Collection<Long> calculationIds) {
        if (calculationIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectByCalculationIds(calculationIds);
    }
}

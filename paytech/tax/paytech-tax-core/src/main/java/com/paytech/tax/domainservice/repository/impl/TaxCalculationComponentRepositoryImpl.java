package com.paytech.tax.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.tax.core.dal.dataobject.TaxCalculationComponent;
import com.paytech.tax.core.dal.mapper.TaxCalculationComponentMapper;
import com.paytech.tax.domainservice.repository.TaxCalculationComponentRepository;

import lombok.AllArgsConstructor;

/** TaxCalculationComponentRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TaxCalculationComponentRepositoryImpl implements TaxCalculationComponentRepository {
    private final TaxCalculationComponentMapper mapper;

    /** 一次插入全部行；空列表不发出 SQL，行数不符为系统异常。 */
    @Override
    public void insertAll(List<TaxCalculationComponent> rows) {
        if (rows.isEmpty()) {
            return;
        }
        if (mapper.batchInsert(rows) != rows.size()) {
            throw new ErrorException("t_tax_calculation_component 批量插入行数不符: 期望 " + rows.size());
        }
    }

    /** 一组计算的全部组件。 */
    @Override
    public List<TaxCalculationComponent> findByCalculationIds(Collection<Long> calculationIds) {
        if (calculationIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectByCalculationIds(calculationIds);
    }
}

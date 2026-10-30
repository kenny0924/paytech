package com.paytech.tax.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.tax.core.dal.dataobject.TaxCalculation;
import com.paytech.tax.core.dal.mapper.TaxCalculationMapper;
import com.paytech.tax.domainservice.repository.TaxCalculationRepository;

import lombok.AllArgsConstructor;

/** TaxCalculationRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TaxCalculationRepositoryImpl implements TaxCalculationRepository {
    private final TaxCalculationMapper mapper;

    /** 插入一行；影响行数不为 1 时为系统异常。 */
    @Override
    public void insert(TaxCalculation row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("t_tax_calculation 插入行数不符: " + row.getId());
        }
    }

    /** 按 ID 读取；不存在返回 null。 */
    @Override
    public TaxCalculation findById(long id) {
        return mapper.selectById(id);
    }

    /** 按计算身份读取（uk_tax_calculation_revision）；不存在返回 null。 */
    @Override
    public TaxCalculation findByIdentity(String sourceSystem, String sourceObjectType, long sourceObjectId, int sourceRevision, String taxScopeKey) {
        return mapper.selectByIdentity(sourceSystem, sourceObjectType, sourceObjectId, sourceRevision, taxScopeKey);
    }

    /** 来源对象编号下的全部计算，按准备版本升序。 */
    @Override
    public List<TaxCalculation> findByObjectNo(String sourceObjectNo) {
        return mapper.selectByObjectNo(sourceObjectNo);
    }
}

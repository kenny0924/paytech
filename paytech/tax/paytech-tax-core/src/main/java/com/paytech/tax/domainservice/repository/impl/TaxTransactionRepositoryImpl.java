package com.paytech.tax.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.tax.core.dal.dataobject.TaxTransaction;
import com.paytech.tax.core.dal.mapper.TaxTransactionMapper;
import com.paytech.tax.domainservice.repository.TaxTransactionRepository;

import lombok.AllArgsConstructor;

/** TaxTransactionRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TaxTransactionRepositoryImpl implements TaxTransactionRepository {
    private final TaxTransactionMapper mapper;

    /** 插入一行；影响行数不为 1 时为系统异常。 */
    @Override
    public void insert(TaxTransaction row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("t_tax_transaction 插入行数不符: " + row.getId());
        }
    }

    /** 按源事实身份读取（uk_tax_transaction_source）；不存在返回 null。 */
    @Override
    public TaxTransaction findBySource(String sourceSystem, String sourceFactType, String sourceFactId, String taxScopeKey) {
        return mapper.selectBySource(sourceSystem, sourceFactType, sourceFactId, taxScopeKey);
    }

    /** 按 ID 读取；不存在返回 null。 */
    @Override
    public TaxTransaction findById(long id) {
        return mapper.selectById(id);
    }

    /** 一组计算对应的正式交易。 */
    @Override
    public List<TaxTransaction> findByCalculationIds(Collection<Long> calculationIds) {
        if (calculationIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectByCalculationIds(calculationIds);
    }
}

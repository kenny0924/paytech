package com.paytech.billing.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.billing.core.dal.dataobject.EconomicFactLine;
import com.paytech.billing.core.dal.mapper.EconomicFactLineMapper;
import com.paytech.billing.domainservice.repository.EconomicFactLineRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** EconomicFactLineRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class EconomicFactLineRepositoryImpl implements EconomicFactLineRepository {
    private final EconomicFactLineMapper mapper;

    /** 经济事实的全部明细行，按行号升序。 */
    @Override
    public List<EconomicFactLine> findByEconomics(long transactionEconomicsId) {
        return mapper.selectByEconomics(transactionEconomicsId);
    }

    /** 一组经济事实的全部明细行；空集合不发出 SQL。 */
    @Override
    public List<EconomicFactLine> findByEconomicsIds(Collection<Long> transactionEconomicsIds) {
        if (transactionEconomicsIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectByEconomicsIds(transactionEconomicsIds);
    }

    /** 一次插入全部明细行并核对影响行数。 */
    @Override
    public void insertAll(List<EconomicFactLine> lines) {
        if (mapper.insertAll(lines) != lines.size()) {
            throw new ErrorException("经济事实明细行插入行数不符: 期望 " + lines.size());
        }
    }
}

package com.paytech.billing.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.billing.core.dal.dataobject.BillingFactLine;
import com.paytech.billing.core.dal.mapper.BillingFactLineMapper;
import com.paytech.billing.domainservice.repository.BillingFactLineRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** BillingFactLineRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class BillingFactLineRepositoryImpl implements BillingFactLineRepository {
    private final BillingFactLineMapper mapper;

    /** 一次插入全部明细行并核对影响行数。 */
    @Override
    public void insertAll(List<BillingFactLine> lines) {
        if (mapper.insertAll(lines) != lines.size()) {
            throw new ErrorException("计费事实明细行插入行数不符: 期望 " + lines.size());
        }
    }

    /** 一条事实的全部明细行。 */
    @Override
    public List<BillingFactLine> findByFactId(long factId) {
        return mapper.selectByFactId(factId);
    }

    /** 一组事实的全部明细行；空集合不发出 SQL。 */
    @Override
    public List<BillingFactLine> findByFactIds(Collection<Long> factIds) {
        if (factIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectByFactIds(factIds);
    }
}

package com.paytech.billing.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.billing.core.dal.dataobject.BillingFactPricing;
import com.paytech.billing.core.dal.mapper.BillingFactPricingMapper;
import com.paytech.billing.domainservice.repository.BillingFactPricingRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** BillingFactPricingRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class BillingFactPricingRepositoryImpl implements BillingFactPricingRepository {
    private final BillingFactPricingMapper mapper;

    /** 插入定价输入并核对影响行数。 */
    @Override
    public void insert(BillingFactPricing pricing) {
        if (mapper.insert(pricing) != 1) {
            throw new ErrorException("计费事实定价输入插入行数不符: " + pricing.getFactId());
        }
    }

    /** 按事实读取定价输入，不存在返回空。 */
    @Override
    public BillingFactPricing findByFactId(long factId) {
        return mapper.selectByFactId(factId);
    }

    /** 按一组事实读取定价输入；空集合不发出 SQL。 */
    @Override
    public List<BillingFactPricing> findByFactIds(Collection<Long> factIds) {
        if (factIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectByFactIds(factIds);
    }
}

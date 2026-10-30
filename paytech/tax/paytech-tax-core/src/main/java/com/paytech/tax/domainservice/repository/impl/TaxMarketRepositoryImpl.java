package com.paytech.tax.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.tax.core.dal.dataobject.TaxMarket;
import com.paytech.tax.core.dal.mapper.TaxMarketMapper;
import com.paytech.tax.domainservice.repository.TaxMarketRepository;

import lombok.AllArgsConstructor;

/** TaxMarketRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TaxMarketRepositoryImpl implements TaxMarketRepository {
    private final TaxMarketMapper mapper;

    /** 纳税主体的全部准入记录（任意地区、任意状态）。 */
    @Override
    public List<TaxMarket> findBySubject(long subjectPartyId) {
        return mapper.selectBySubject(subjectPartyId);
    }

    /** 按 ID 批量读取；空集合不发出 SQL。 */
    @Override
    public List<TaxMarket> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectByIds(ids);
    }
}

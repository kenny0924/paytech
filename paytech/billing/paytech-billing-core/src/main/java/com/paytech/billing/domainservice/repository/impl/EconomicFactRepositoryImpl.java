package com.paytech.billing.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.billing.core.dal.dataobject.EconomicFact;
import com.paytech.billing.core.dal.mapper.EconomicFactMapper;
import com.paytech.billing.domainservice.repository.EconomicFactRepository;

import lombok.AllArgsConstructor;

/** EconomicFactRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class EconomicFactRepositoryImpl implements EconomicFactRepository {
    private final EconomicFactMapper mapper;

    /** 按主键读取，不存在返回空。 */
    @Override
    public EconomicFact findById(long id) {
        return mapper.selectById(id);
    }
}

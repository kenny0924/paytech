package com.paytech.billing.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.billing.core.dal.dataobject.EconomicFactLine;
import com.paytech.billing.core.dal.mapper.EconomicFactLineMapper;
import com.paytech.billing.domainservice.repository.EconomicFactLineRepository;

import lombok.AllArgsConstructor;

/** EconomicFactLineRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class EconomicFactLineRepositoryImpl implements EconomicFactLineRepository {
    private final EconomicFactLineMapper mapper;

    /** 交易经济结果的全部组件，按组件序号升序。 */
    @Override
    public List<EconomicFactLine> findByEconomics(long transactionEconomicsId) {
        return mapper.selectByEconomics(transactionEconomicsId);
    }
}

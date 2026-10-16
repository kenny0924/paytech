package com.paytech.finance.accounting.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.TransactionEconomicComponent;
import com.paytech.finance.accounting.core.dal.mapper.TransactionEconomicComponentMapper;
import com.paytech.finance.accounting.domainservice.repository.TransactionEconomicComponentRepository;

import lombok.AllArgsConstructor;

/** TransactionEconomicComponentRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TransactionEconomicComponentRepositoryImpl implements TransactionEconomicComponentRepository {
    private final TransactionEconomicComponentMapper mapper;

    /** 交易经济结果的全部组件，按组件序号升序。 */
    @Override
    public List<TransactionEconomicComponent> findByEconomics(long transactionEconomicsId) {
        return mapper.selectByEconomics(transactionEconomicsId);
    }
}

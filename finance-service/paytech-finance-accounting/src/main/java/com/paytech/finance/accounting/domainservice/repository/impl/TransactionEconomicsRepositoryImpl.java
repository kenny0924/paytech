package com.paytech.finance.accounting.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.TransactionEconomics;
import com.paytech.finance.accounting.core.dal.mapper.TransactionEconomicsMapper;
import com.paytech.finance.accounting.domainservice.repository.TransactionEconomicsRepository;

import lombok.AllArgsConstructor;

/** TransactionEconomicsRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TransactionEconomicsRepositoryImpl implements TransactionEconomicsRepository {
    private final TransactionEconomicsMapper mapper;

    /** 按主键读取，不存在返回空。 */
    @Override
    public TransactionEconomics findById(long id) {
        return mapper.selectById(id);
    }
}

package com.paytech.account.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.account.core.dal.dataobject.BalanceDiscrepancy;
import com.paytech.account.core.dal.mapper.BalanceDiscrepancyMapper;
import com.paytech.account.domainservice.repository.BalanceDiscrepancyRepository;

import lombok.AllArgsConstructor;

/** BalanceDiscrepancyRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class BalanceDiscrepancyRepositoryImpl implements BalanceDiscrepancyRepository {
    private final BalanceDiscrepancyMapper mapper;

    /** 写入或更新同日差异。 */
    @Override
    public void upsert(BalanceDiscrepancy discrepancy) {
        mapper.upsert(discrepancy);
    }
}

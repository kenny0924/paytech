package com.paytech.ledger.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.ledger.core.dal.dataobject.LedgerPeriodBalance;
import com.paytech.ledger.core.dal.mapper.LedgerPeriodBalanceMapper;
import com.paytech.ledger.domainservice.repository.LedgerPeriodBalanceRepository;

import lombok.AllArgsConstructor;

/** LedgerPeriodBalanceRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class LedgerPeriodBalanceRepositoryImpl implements LedgerPeriodBalanceRepository {
    private final LedgerPeriodBalanceMapper mapper;

    /** 按水位条件写入期间余额；空列表不访问数据库。 */
    @Override
    public void upsert(List<LedgerPeriodBalance> balances) {
        if (!balances.isEmpty()) {
            mapper.upsertBatch(balances);
        }
    }
}

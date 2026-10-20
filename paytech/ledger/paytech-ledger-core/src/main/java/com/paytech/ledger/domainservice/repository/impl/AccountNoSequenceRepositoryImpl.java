package com.paytech.ledger.domainservice.repository.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.ledger.core.dal.mapper.AccountNoSequenceMapper;
import com.paytech.ledger.domainservice.repository.AccountNoSequenceRepository;

import lombok.AllArgsConstructor;

/** AccountNoSequenceRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class AccountNoSequenceRepositoryImpl implements AccountNoSequenceRepository {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final AccountNoSequenceMapper mapper;

    /** 分配与读取 LAST_INSERT_ID() 必须在同一连接，因此要求调用方事务。 */
    @Override
    public long next(long ledgerBookId, long chartAccountId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("账户号序号必须在建户事务内分配");
        }
        mapper.allocate(idGenerator.nextId(), ledgerBookId, chartAccountId,
            clock.instant().truncatedTo(ChronoUnit.MICROS));
        return mapper.selectAllocated();
    }
}

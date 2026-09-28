package com.paytech.finance.accounting.domainservice.repository.impl;

import com.paytech.finance.accounting.core.dal.dataobject.BalanceAccountRow;
import com.paytech.finance.accounting.core.dal.dataobject.LedgerBookRow;
import com.paytech.finance.accounting.core.dal.mapper.BalanceAccountMapper;
import com.paytech.finance.accounting.domain.enums.OwnerType;
import com.paytech.finance.accounting.domainservice.repository.BalanceAccountRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 隔离 MyBatis Mapper 的余额读取仓储。 */
@Repository
@RequiredArgsConstructor
public class BalanceAccountRepositoryImpl implements BalanceAccountRepository {
    private final BalanceAccountMapper mapper;

    @Override
    public List<LedgerBookRow> operatingEntityBooks(long operatingEntityId, String currency) {
        return mapper.ledgerBooks(operatingEntityId, currency);
    }

    @Override
    public List<BalanceAccountRow> ownerAccounts(long ledgerBookId, OwnerType ownerType, long ownerId) {
        return mapper.ownerAccounts(ledgerBookId, ownerType, ownerId);
    }
}

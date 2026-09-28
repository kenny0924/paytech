package com.paytech.finance.accounting.domainservice.repository;

import com.paytech.finance.accounting.core.dal.dataobject.BalanceAccountRow;
import com.paytech.finance.accounting.core.dal.dataobject.LedgerBookRow;
import com.paytech.finance.accounting.domain.enums.OwnerType;
import java.util.List;

/** 余额查询对账簿和账户投影的领域访问边界。 */
public interface BalanceAccountRepository {
    List<LedgerBookRow> operatingEntityBooks(long operatingEntityId, String currency);

    List<BalanceAccountRow> ownerAccounts(long ledgerBookId, OwnerType ownerType, long ownerId);
}

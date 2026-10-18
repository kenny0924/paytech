package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.LedgerPeriodBalance;

/** t_ledger_period_balance：期间余额投影写入。 */
public interface LedgerPeriodBalanceRepository {

    /** 按水位条件写入期间余额；空列表不访问数据库。 */
    void upsert(List<LedgerPeriodBalance> balances);
}

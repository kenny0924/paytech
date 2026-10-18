package com.paytech.account.domainservice.repository;

import com.paytech.account.core.dal.dataobject.BalanceDiscrepancy;

/** t_balance_discrepancy：日终核对差异写入。 */
public interface BalanceDiscrepancyRepository {

    /** 写入差异；同日同账簿同账户已记录时更新为最新差额。 */
    void upsert(BalanceDiscrepancy discrepancy);
}

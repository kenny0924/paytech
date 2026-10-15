package com.paytech.finance.accounting.domainservice.repository;

import com.paytech.finance.accounting.core.dal.dataobject.ChartOfAccounts;

/** t_ChartOfAccounts：科目表读取。 */
public interface ChartOfAccountsRepository {

    /** 按科目表代码读取，不存在返回空。 */
    ChartOfAccounts findByChartCode(String chartCode);
}

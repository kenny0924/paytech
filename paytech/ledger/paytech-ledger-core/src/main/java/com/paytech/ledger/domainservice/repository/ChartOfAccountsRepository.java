package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;

/** t_ChartOfAccounts：科目表读取。 */
public interface ChartOfAccountsRepository {

    /** 按科目表代码读取，不存在返回空。 */
    ChartOfAccounts findByChartCode(String chartCode);

    /** 读取全部科目表，按科目表代码升序。 */
    List<ChartOfAccounts> findAll();
}

package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.TrialBalanceLine;
import com.paytech.ledger.domain.view.AccountBalanceView;

/** t_trial_balance_line：试算行追加与读取。 */
public interface TrialBalanceLineRepository {

    /** 批量插入试算行；空列表不访问数据库。 */
    void insertBatch(List<TrialBalanceLine> lines);

    /** 读取一次试算的全部行及科目。 */
    List<AccountBalanceView> findViews(long runId);
}

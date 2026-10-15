package com.paytech.finance.accounting.domainservice.repository;

import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceException;
import com.paytech.finance.accounting.domain.view.TrialBalanceFindingView;

/** t_trial_balance_exception：试算异常追加与读取。 */
public interface TrialBalanceExceptionRepository {

    /** 批量插入异常；空列表不访问数据库。 */
    void insertBatch(List<TrialBalanceException> exceptions);

    /** 读取一次试算的全部异常。 */
    List<TrialBalanceFindingView> findViews(long runId);
}

package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.TrialBalanceRun;
import com.paytech.ledger.domain.view.TrialBalanceRunView;

/** t_trial_balance_run：试算运行读写。 */
public interface TrialBalanceRunRepository {

    /** 插入终态试算运行；同一账簿期间序号冲突说明并发试算。 */
    void insert(TrialBalanceRun run);

    /** 下一个试算序号：当前最大序号加一。 */
    int nextRunNo(long ledgerBookId, long periodId);

    /** 按序号读取试算摘要，不存在返回空。 */
    TrialBalanceRunView findView(long ledgerBookId, long periodId, int runNo);

    /** 按序号降序分页读取试算摘要。 */
    List<TrialBalanceRunView> findPage(long ledgerBookId, long periodId, int size, int offset);

    /** 统计账簿期间的试算次数。 */
    long countPage(long ledgerBookId, long periodId);
}

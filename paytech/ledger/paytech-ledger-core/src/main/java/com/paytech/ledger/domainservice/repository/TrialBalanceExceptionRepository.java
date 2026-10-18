package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.TrialBalanceException;
import com.paytech.ledger.core.enums.TrialExceptionStatus;
import com.paytech.ledger.domain.view.TrialBalanceFindingView;

/** t_trial_balance_exception：试算异常追加与读取。 */
public interface TrialBalanceExceptionRepository {

    /** 批量插入异常；空列表不访问数据库。 */
    void insertBatch(List<TrialBalanceException> exceptions);

    /** 读取一次试算的全部异常。 */
    List<TrialBalanceFindingView> findViews(long runId);

    /** 按主键读取异常，不存在返回空。 */
    TrialBalanceException findById(long id);

    /** 统计一次试算中仍为 OPEN 或 UNDER_REVIEW 的异常。 */
    long countUnresolvedByRun(long runId);

    /**
     * 以原状态为条件写入调用方已在对象上设置的解决字段并迁移到新状态，成功后同步原对象。
     * <li>原状态已变化时以并发冲突失败。</li>
     */
    void updateResolution(TrialBalanceException exception, TrialExceptionStatus status);
}

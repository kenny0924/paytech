package com.paytech.finance.accounting.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.enums.TrialExceptionStatus;
import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceException;
import com.paytech.finance.accounting.domain.view.TrialBalanceFindingView;

/** t_trial_balance_exception：试算异常持久化。 */
@Mapper
public interface TrialBalanceExceptionMapper {

    /** 批量插入异常发现事实。 */
    int insertBatch(@Param("rows") List<TrialBalanceException> rows);

    /** 读取一次试算的全部异常，按类型、引用类型与引用主键排序。 */
    List<TrialBalanceFindingView> selectViews(@Param("runId") long runId);

    /** 按主键读取异常，不存在返回空。 */
    TrialBalanceException selectById(@Param("id") long id);

    /** 统计一次试算中仍为 OPEN 或 UNDER_REVIEW 的异常。 */
    long countUnresolvedByRun(@Param("runId") long runId);

    /**
     * 按原状态与解决方式条件迁移异常状态，并写入当前步骤的解决字段。
     *
     * @return 受影响行数
     */
    int updateResolution(@Param("exception") TrialBalanceException exception, @Param("status") TrialExceptionStatus status,
        @Param("now") Instant now);
}

package com.paytech.finance.accounting.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceException;
import com.paytech.finance.accounting.domain.view.TrialBalanceFindingView;

/** t_trial_balance_exception：试算异常持久化。 */
@Mapper
public interface TrialBalanceExceptionMapper {

    /** 批量插入异常发现事实。 */
    int insertBatch(@Param("rows") List<TrialBalanceException> rows);

    /** 读取一次试算的全部异常，按类型、引用类型与引用主键排序。 */
    List<TrialBalanceFindingView> selectViews(@Param("runId") long runId);
}

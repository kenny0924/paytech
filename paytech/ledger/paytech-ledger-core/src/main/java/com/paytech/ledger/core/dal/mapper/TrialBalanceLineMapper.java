package com.paytech.ledger.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.TrialBalanceLine;
import com.paytech.ledger.domain.view.AccountBalanceView;

/** t_trial_balance_line：试算行持久化（只追加）。 */
@Mapper
public interface TrialBalanceLineMapper {

    /** 批量插入试算行。 */
    int insertBatch(@Param("rows") List<TrialBalanceLine> rows);

    /** 读取一次试算的全部行及其科目，按科目编号排序。 */
    List<AccountBalanceView> selectViews(@Param("runId") long runId);
}

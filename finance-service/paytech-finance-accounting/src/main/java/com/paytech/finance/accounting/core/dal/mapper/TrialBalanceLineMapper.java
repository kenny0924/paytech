package com.paytech.finance.accounting.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceLine;
import com.paytech.finance.accounting.domain.view.TrialBalanceLineView;

/** t_trial_balance_line：试算行持久化（只追加）。 */
@Mapper
public interface TrialBalanceLineMapper {

    /** 批量插入试算行。 */
    int insertBatch(@Param("rows") List<TrialBalanceLine> rows);

    /** 读取一次试算的全部行及其科目，按科目代码排序。 */
    List<TrialBalanceLineView> selectViews(@Param("runId") long runId);
}

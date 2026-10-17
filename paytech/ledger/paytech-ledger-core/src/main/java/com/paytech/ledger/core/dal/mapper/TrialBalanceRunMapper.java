package com.paytech.ledger.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.TrialBalanceRun;
import com.paytech.ledger.domain.view.TrialBalanceRunView;

/** t_trial_balance_run：试算平衡运行持久化。 */
@Mapper
public interface TrialBalanceRunMapper {

    /** 插入终态试算运行。 */
    int insert(TrialBalanceRun record);

    /** 读取同一账簿期间当前最大试算序号，没有时为 0。 */
    int selectMaxRunNo(@Param("ledgerBookId") long ledgerBookId, @Param("periodId") long periodId);

    /** 按序号读取试算摘要，不存在返回空。 */
    TrialBalanceRunView selectView(@Param("ledgerBookId") long ledgerBookId, @Param("periodId") long periodId,
        @Param("runNo") int runNo);

    /** 按序号降序分页读取试算摘要。 */
    List<TrialBalanceRunView> selectPage(@Param("ledgerBookId") long ledgerBookId, @Param("periodId") long periodId,
        @Param("size") int size, @Param("offset") int offset);

    /** 统计账簿期间的试算次数。 */
    long countPage(@Param("ledgerBookId") long ledgerBookId, @Param("periodId") long periodId);
}

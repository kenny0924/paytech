package com.paytech.ledger.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.LedgerPeriodBalance;

/** t_ledger_period_balance：期间科目余额投影。 */
@Mapper
public interface LedgerPeriodBalanceMapper {

    /**
     * 按（期间, 账簿, 科目）条件写入期间余额：新水位不低于已有水位才覆盖，as_of_sequence 最后更新
     *
     * @return 受影响行数
     */
    int upsertBatch(@Param("rows") List<LedgerPeriodBalance> rows);
}

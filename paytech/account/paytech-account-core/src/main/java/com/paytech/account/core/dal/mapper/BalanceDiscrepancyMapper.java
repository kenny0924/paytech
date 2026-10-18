package com.paytech.account.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.paytech.account.core.dal.dataobject.BalanceDiscrepancy;

/** t_balance_discrepancy：日终核对差异持久化。 */
@Mapper
public interface BalanceDiscrepancyMapper {

    /** 插入差异；同日同账簿同账户已存在时更新两侧余额与差额。 */
    int upsert(BalanceDiscrepancy record);
}

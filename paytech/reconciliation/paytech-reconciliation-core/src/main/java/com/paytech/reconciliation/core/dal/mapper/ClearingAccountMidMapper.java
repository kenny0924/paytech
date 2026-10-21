package com.paytech.reconciliation.core.dal.mapper;

import java.util.List;

import com.paytech.reconciliation.core.dal.dataobject.ClearingAccountMid;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** t_clearing_account_mid：清算账户所含 MID的 MyBatis 映射；SQL 位于 mapper/reconciliation/ClearingAccountMidMapper.xml。 */
@Mapper
public interface ClearingAccountMidMapper {
    /** 插入一行；主键由应用分配。 */
    int insert(ClearingAccountMid record);

    /** 按主键删除一行。 */
    int deleteById(@Param("id") Long id);

    /** 按 MID + 币种读取（唯一）。 */
    ClearingAccountMid selectByMidAndCurrency(@Param("midId") Long midId, @Param("currency") String currency);

    /** 读取一个清算账户的全部 MID。 */
    List<ClearingAccountMid> selectByClearingAccount(@Param("clearingAccountId") Long clearingAccountId);

    /** 按 MID 集合读取归属行（全部币种）。 */
    List<ClearingAccountMid> selectByMidIds(@Param("midIds") List<Long> midIds);
}

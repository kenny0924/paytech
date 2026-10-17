package com.paytech.account.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.dal.dataobject.BalanceBook;
import com.paytech.account.domain.view.OwnerAccountBalanceView;

/** 余额查询的只读 SQL：共库过渡期读取 ledger 的 t_ledger_book、t_account 与 t_chart_account。 */
@Mapper
public interface BalanceAccountMapper {

    /** 按 entityNo 与币种读取 OE 账簿。 */
    BalanceBook selectOperatingEntityBook(@Param("entityNo") String entityNo, @Param("currency") String currency);

    /** 读取 Owner 在账簿内的全部账户及其科目组合。 */
    List<OwnerAccountBalanceView> selectOwnerBalances(@Param("ledgerBookId") long ledgerBookId,
        @Param("ownerType") OwnerType ownerType, @Param("ownerId") String ownerId);
}

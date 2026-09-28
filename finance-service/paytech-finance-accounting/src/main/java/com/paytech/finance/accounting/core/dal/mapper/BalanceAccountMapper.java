package com.paytech.finance.accounting.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.BalanceAccountRow;
import com.paytech.finance.accounting.core.dal.dataobject.LedgerBookRow;
import com.paytech.finance.accounting.domain.enums.OwnerType;

/** Client/Local Partner 当前余额所需的最小本库读取。 */
@Mapper
public interface BalanceAccountMapper {
    List<LedgerBookRow> ledgerBooks(@Param("operatingEntityId") long operatingEntityId,
        @Param("currency") String currency);

    List<BalanceAccountRow> ownerAccounts(@Param("ledgerBookId") long ledgerBookId,
        @Param("ownerType") OwnerType ownerType, @Param("ownerId") long ownerId);
}

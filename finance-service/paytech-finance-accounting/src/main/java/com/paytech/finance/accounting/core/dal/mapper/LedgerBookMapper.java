package com.paytech.finance.accounting.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.LedgerBook;
import com.paytech.finance.accounting.core.enums.LedgerEntityType;

/** t_ledger_book：账簿持久化。 */
@Mapper
public interface LedgerBookMapper {

    /** 插入一行。 */
    int insert(LedgerBook record);

    /** 按主键读取。 */
    LedgerBook selectById(@Param("id") long id);

    /** 按主体类型、主体标识和币种读取唯一账簿。 */
    LedgerBook selectByEntity(@Param("entityType") LedgerEntityType entityType, @Param("entityId") String entityId,
        @Param("currency") String currency);
}

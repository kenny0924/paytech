package com.paytech.finance.accounting.domainservice.repository;

import com.paytech.finance.accounting.core.dal.dataobject.LedgerBook;
import com.paytech.finance.accounting.core.enums.LedgerEntityType;

/** t_LedgerBook：账簿读写。 */
public interface LedgerBookRepository {

    /** 插入账簿；同主体同币种已有账簿时拒绝。 */
    void insert(LedgerBook book);

    /** 按主键读取账簿，不存在返回空。 */
    LedgerBook findById(long id);

    /** 按主体与币种读取唯一账簿，不存在返回空。 */
    LedgerBook findByEntity(LedgerEntityType entityType, String entityId, String currency);
}

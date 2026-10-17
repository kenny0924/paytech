package com.paytech.ledger.domainservice.repository;

import com.paytech.ledger.core.dal.dataobject.FinanceEntity;

/** t_FinanceEntity：财务主体读写。 */
public interface FinanceEntityRepository {

    /** 插入财务主体；代码或法律 Party 已存在时拒绝。 */
    void insert(FinanceEntity entity);

    /** 按主键读取财务主体，不存在返回空。 */
    FinanceEntity findById(long id);
}

package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.domain.FinanceEntityQuery;

/** t_FinanceEntity：财务主体读写。 */
public interface FinanceEntityRepository {

    /** 插入财务主体；代码或法律 Party 已存在时拒绝。 */
    void insert(FinanceEntity entity);

    /** 按主键读取财务主体，不存在返回空。 */
    FinanceEntity findById(long id);

    /** 按法律 Party 读取财务主体（法律 Party 唯一），不存在返回空。 */
    FinanceEntity findByLegalPartyId(long legalPartyId);

    /** 按过滤条件分页读取财务主体，按主键升序。 */
    List<FinanceEntity> findPage(FinanceEntityQuery query);

    /** 统计符合过滤条件的财务主体数量。 */
    long countPage(FinanceEntityQuery query);
}

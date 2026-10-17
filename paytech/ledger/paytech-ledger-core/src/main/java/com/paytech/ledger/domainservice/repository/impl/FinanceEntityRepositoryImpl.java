package com.paytech.ledger.domainservice.repository.impl;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.mapper.FinanceEntityMapper;
import com.paytech.ledger.domainservice.repository.FinanceEntityRepository;

import lombok.AllArgsConstructor;

/** FinanceEntityRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class FinanceEntityRepositoryImpl implements FinanceEntityRepository {
    private final FinanceEntityMapper mapper;

    /** 插入财务主体；代码或法律 Party 已存在时拒绝。 */
    @Override
    public void insert(FinanceEntity entity) {
        insertUnique(() -> mapper.insert(entity), "财务主体代码或法律 Party 已存在: " + entity.getEntityCode());
    }

    /** 按主键读取财务主体，不存在返回空。 */
    @Override
    public FinanceEntity findById(long id) {
        return mapper.selectById(id);
    }

    /** 执行插入，业务唯一键冲突时转换为带上下文的业务拒绝。 */
    private void insertUnique(Runnable insert, String message) {
        try {
            insert.run();
        } catch (DuplicateKeyException e) {
            throw new BizCheckFailException(message, e);
        }
    }
}

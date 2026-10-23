package com.paytech.ledger.domainservice.repository.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.FailException;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.mapper.FinanceEntityMapper;
import com.paytech.ledger.domain.FinanceEntityQuery;
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
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new FailException(LedgerSetupErrorCode.FINANCE_ENTITY_ALREADY_EXISTS, null, e);
        }
    }

    /** 按主键读取财务主体，不存在返回空。 */
    @Override
    public FinanceEntity findById(long id) {
        return mapper.selectById(id);
    }

    /** 按法律 Party 读取财务主体（法律 Party 唯一），不存在返回空。 */
    @Override
    public FinanceEntity findByLegalPartyId(long legalPartyId) {
        return mapper.selectByLegalPartyId(legalPartyId);
    }

    /** 按过滤条件分页读取财务主体，按主键升序。 */
    @Override
    public List<FinanceEntity> findPage(FinanceEntityQuery query) {
        return mapper.selectPage(query, query.getPaging().size(), query.getPaging().offset());
    }

    /** 统计符合过滤条件的财务主体数量。 */
    @Override
    public long countPage(FinanceEntityQuery query) {
        return mapper.countPage(query);
    }
}

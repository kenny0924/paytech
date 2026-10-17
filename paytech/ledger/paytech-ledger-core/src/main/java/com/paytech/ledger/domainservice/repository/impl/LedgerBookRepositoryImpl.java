package com.paytech.ledger.domainservice.repository.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.dal.mapper.LedgerBookMapper;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.domain.LedgerBookQuery;
import com.paytech.ledger.domain.view.LedgerBookView;
import com.paytech.ledger.domainservice.repository.LedgerBookRepository;

import lombok.AllArgsConstructor;

/** LedgerBookRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class LedgerBookRepositoryImpl implements LedgerBookRepository {
    private final LedgerBookMapper mapper;

    /** 插入账簿；同主体同币种已有账簿时拒绝。 */
    @Override
    public void insert(LedgerBook book) {
        insertUnique(() -> mapper.insert(book), "账簿已存在: " + book.getEntityType().getCode() + "/" + book.getEntityId() + "/" + book.getCurrency());
    }

    /** 按主键读取账簿，不存在返回空。 */
    @Override
    public LedgerBook findById(long id) {
        return mapper.selectById(id);
    }

    /** 按主体与币种读取唯一账簿，不存在返回空。 */
    @Override
    public LedgerBook findByEntity(LedgerEntityType entityType, String entityId, String currency) {
        return mapper.selectByEntity(entityType, entityId, currency);
    }

    /** 按过滤条件分页读取账簿，按主键升序。 */
    @Override
    public List<LedgerBookView> findPage(LedgerBookQuery query) {
        return mapper.selectPage(query, query.getPaging().size(), query.getPaging().offset());
    }

    /** 统计符合过滤条件的账簿数量。 */
    @Override
    public long countPage(LedgerBookQuery query) {
        return mapper.countPage(query);
    }

    /** 按主键升序读取主体的全部币种账簿。 */
    @Override
    public List<LedgerBook> findAllByEntity(LedgerEntityType entityType, String entityId) {
        return mapper.selectAllByEntity(entityType, entityId);
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

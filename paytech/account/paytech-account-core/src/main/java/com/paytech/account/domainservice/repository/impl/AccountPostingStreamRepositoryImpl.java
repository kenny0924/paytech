package com.paytech.account.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.core.dal.dataobject.AccountPostingStream;
import com.paytech.account.core.dal.mapper.AccountPostingStreamMapper;
import com.paytech.account.domainservice.repository.AccountPostingStreamRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** AccountPostingStreamRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class AccountPostingStreamRepositoryImpl implements AccountPostingStreamRepository {
    private final AccountPostingStreamMapper mapper;

    /** 唯一键冲突说明同一流刚被并发创建，转为可重试的并发冲突。 */
    @Override
    public void insert(AccountPostingStream stream) {
        try {
            mapper.insert(stream);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("入账流被并发创建: " + stream.getEntityType().getCode() + "/"
                + stream.getEntityId() + "/" + stream.getCurrency(), e);
        }
    }

    /** 以排他锁读取一条流。 */
    @Override
    public AccountPostingStream lock(AccountEntityType entityType, String entityId, String currency, int shardNo) {
        return mapper.selectForUpdate(entityType, entityId, currency, shardNo);
    }

    /** 读取主体与币种下的全部流。 */
    @Override
    public List<AccountPostingStream> findByEntity(AccountEntityType entityType, String entityId, String currency) {
        return mapper.selectByEntity(entityType, entityId, currency);
    }

    /** 空集合不查询。 */
    @Override
    public List<AccountPostingStream> findByEntities(AccountEntityType entityType, List<String> entityIds,
        String currency) {
        return entityIds.isEmpty() ? List.of() : mapper.selectByEntities(entityType, entityIds, currency);
    }

    /** 以原序号 CAS 加一，成功后同步原对象。 */
    @Override
    public void advance(AccountPostingStream stream, Instant postedTime) {
        if (mapper.advance(stream, postedTime) != 1) {
            throw new ConcurrentRequestException("入账流序号已被并发分配: " + stream.getId());
        }
        stream.setLastSequence(stream.getLastSequence() + 1);
        stream.setLastPostedTime(postedTime);
        stream.setUpdateTime(postedTime);
    }
}

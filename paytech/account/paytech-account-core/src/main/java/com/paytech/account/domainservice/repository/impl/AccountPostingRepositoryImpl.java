package com.paytech.account.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.core.dal.dataobject.AccountPosting;
import com.paytech.account.core.dal.mapper.AccountPostingMapper;
import com.paytech.account.domainservice.repository.AccountPostingRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** AccountPostingRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class AccountPostingRepositoryImpl implements AccountPostingRepository {
    private final AccountPostingMapper mapper;

    /** 唯一键冲突转为可重试的并发冲突。 */
    @Override
    public void insert(AccountPosting posting) {
        try {
            mapper.insert(posting);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("入账批次被并发登记: " + posting.getEntityId() + "/"
                + posting.getCurrency() + "/" + posting.getPostingSequence(), e);
        }
    }

    /** 按主键读取。 */
    @Override
    public AccountPosting findById(long id) {
        return mapper.selectById(id);
    }

    /** 空集合不查询。 */
    @Override
    public List<AccountPosting> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }

    /** 按流与序号读取。 */
    @Override
    public AccountPosting findBySequence(AccountEntityType entityType, String entityId, String currency, int shardNo,
        long postingSequence) {
        return mapper.selectBySequence(entityType, entityId, currency, shardNo, postingSequence);
    }

    /** 按序号升序读取一页。 */
    @Override
    public List<AccountPosting> findFromSequence(AccountEntityType entityType, String entityId, String currency,
        int shardNo, long fromSequence, int limit) {
        return mapper.selectFromSequence(entityType, entityId, currency, shardNo, fromSequence, limit);
    }

    /** 只在首次确认时写入；重复发布不覆盖首次时间。 */
    @Override
    public void markPublished(AccountPosting posting, Instant publishedTime) {
        if (mapper.markPublished(posting.getId(), publishedTime) == 1) {
            posting.setPublishedTime(publishedTime);
        }
    }
}

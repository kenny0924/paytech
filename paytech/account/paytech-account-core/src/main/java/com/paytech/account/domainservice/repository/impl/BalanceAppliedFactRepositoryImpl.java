package com.paytech.account.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.account.core.dal.dataobject.BalanceAppliedEntry;
import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.core.dal.mapper.BalanceAppliedFactMapper;
import com.paytech.account.core.enums.AppliedFactStatus;
import com.paytech.account.core.enums.BookEntityType;
import com.paytech.account.domain.BookIdentity;
import com.paytech.account.domainservice.repository.BalanceAppliedFactRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** BalanceAppliedFactRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class BalanceAppliedFactRepositoryImpl implements BalanceAppliedFactRepository {
    private final Clock clock;
    private final BalanceAppliedFactMapper mapper;

    /** 唯一键冲突转为可重试的并发冲突。 */
    @Override
    public void insert(BalanceAppliedFact fact) {
        try {
            mapper.insert(fact);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("经济事实被并发登记: " + fact.getFactId() + "/" + fact.getFactVersion(), e);
        }
    }

    /** 以排他锁读取处理记录。 */
    @Override
    public BalanceAppliedFact lockByFactId(long factId) {
        return mapper.selectByFactIdForUpdate(factId);
    }

    /** 条件迁移，成功后同步原对象。 */
    @Override
    public void updateStatus(BalanceAppliedFact fact, AppliedFactStatus status) {
        Instant now = now();
        if (mapper.updateStatus(fact, status, now) != 1) {
            throw new ConcurrentRequestException("经济事实处理状态已变化: " + fact.getFactId());
        }
        fact.setApplyStatus(status);
        fact.setUpdateTime(now);
    }

    /** 账簿主体内已处理的事实数。 */
    @Override
    public long countProcessed(BookIdentity book) {
        return ((Number)mapper.selectProgress(book.entityType(), book.entityId(), book.currency()).get("factCount")).longValue();
    }

    /** 账簿主体内已处理事实的最大 factId。 */
    @Override
    public Long maxProcessedFactId(BookIdentity book) {
        Object max = mapper.selectProgress(book.entityType(), book.entityId(), book.currency()).get("maxFactId");
        return max == null ? null : ((Number)max).longValue();
    }

    /** 统计 factId 大于给定值的处理记录。 */
    @Override
    public long countAfter(BookIdentity book, long factId) {
        return mapper.countAfter(book.entityType(), book.entityId(), book.currency(), factId);
    }

    /** 余额变化先于处理记录删除；只允许经营主体账簿。 */
    @Override
    public void deleteUpTo(BookIdentity book, long factId) {
        if (book.entityType() != BookEntityType.OPERATING_ENTITY) {
            throw new ErrorException("只能按经营主体账簿删除处理记录: " + book);
        }
        mapper.deleteEntriesUpTo(book.entityType(), book.entityId(), book.currency(), factId);
        mapper.deleteFactsUpTo(book.entityType(), book.entityId(), book.currency(), factId);
    }

    /** 空集合不写。 */
    @Override
    public void insertEntries(List<BalanceAppliedEntry> entries) {
        if (!entries.isEmpty()) {
            mapper.insertEntries(entries);
        }
    }

    /** 读取一条已处理事实的全部余额变化。 */
    @Override
    public List<BalanceAppliedEntry> findEntries(long appliedFactId) {
        return mapper.selectEntries(appliedFactId);
    }

    /** 当前 UTC 时间，截断到微秒。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}

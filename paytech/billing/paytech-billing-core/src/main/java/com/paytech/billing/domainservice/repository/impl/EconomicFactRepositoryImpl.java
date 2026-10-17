package com.paytech.billing.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.billing.api.enums.EconomicFactStatus;
import com.paytech.billing.core.dal.dataobject.EconomicFact;
import com.paytech.billing.core.dal.mapper.EconomicFactMapper;
import com.paytech.billing.domain.EconomicFactBookScope;
import com.paytech.billing.domain.view.EconomicFactPublicationView;
import com.paytech.billing.domainservice.repository.EconomicFactRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** EconomicFactRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class EconomicFactRepositoryImpl implements EconomicFactRepository {
    private final EconomicFactMapper mapper;

    /** 按主键读取，不存在返回空。 */
    @Override
    public EconomicFact findById(long id) {
        return mapper.selectById(id);
    }

    /** 按来源系统与来源事件键读取，不存在返回空。 */
    @Override
    public EconomicFact findBySource(String sourceSystem, String sourceEventKey) {
        return mapper.selectBySource(sourceSystem, sourceEventKey);
    }

    /** 插入 DRAFT 事实头。 */
    @Override
    public void insert(EconomicFact fact) {
        mapper.insert(fact);
    }

    /** DRAFT → APPROVED；影响行数不为 1 时并发冲突，成功后同步状态、版本、复核人与时间。 */
    @Override
    public void approve(EconomicFact fact, long factVersion, long checkerId, Instant now) {
        if (mapper.approve(fact.getId(), factVersion, checkerId, now) != 1) {
            throw new ConcurrentRequestException("经济事实已不是待批准状态: " + fact.getId());
        }
        fact.setStatus(EconomicFactStatus.APPROVED);
        fact.setFactVersion(factVersion);
        fact.setCheckerId(checkerId);
        fact.setApprovedTime(now);
        fact.setUpdateTime(now);
    }

    /** APPROVED → SUPERSEDED；影响行数不为 1 时并发冲突，成功后同步状态与时间。 */
    @Override
    public void supersede(EconomicFact fact, Instant now) {
        if (mapper.supersede(fact.getId(), fact.getFactVersion(), now) != 1) {
            throw new ConcurrentRequestException("被替代经济事实已被替代或版本变化: " + fact.getId());
        }
        fact.setStatus(EconomicFactStatus.SUPERSEDED);
        fact.setUpdateTime(now);
    }

    /** 首次发布确认时写入 published_at；已写入过时不改原对象。 */
    @Override
    public boolean markPublished(EconomicFact fact, Instant now) {
        if (mapper.markPublished(fact.getId(), fact.getFactVersion(), now) != 1) {
            return false;
        }
        fact.setPublishedAt(now);
        fact.setUpdateTime(now);
        return true;
    }

    /** 账簿主体下 ID 大于游标的已批准事实。 */
    @Override
    public List<EconomicFact> findRecordedByBook(EconomicFactBookScope scope, long fromFactId, int limit) {
        return mapper.selectRecordedByBook(scope.getEntityNo(), scope.getFinanceEntityId(), scope.getCurrency(),
            fromFactId, limit);
    }

    /** 账簿主体下已批准事实的发布进度。 */
    @Override
    public EconomicFactPublicationView publicationProgress(EconomicFactBookScope scope) {
        return mapper.selectPublicationProgress(scope.getEntityNo(), scope.getFinanceEntityId(), scope.getCurrency());
    }
}

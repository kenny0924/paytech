package com.paytech.billing.domainservice.repository.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.core.dal.mapper.BillingFactMapper;
import com.paytech.billing.domain.BillingFactBookScope;
import com.paytech.billing.domain.view.BillingFactProgressView;
import com.paytech.billing.domainservice.repository.BillingFactRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** BillingFactRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class BillingFactRepositoryImpl implements BillingFactRepository {
    private final BillingFactMapper mapper;

    /** 插入事实头并核对影响行数。 */
    @Override
    public void insert(BillingFact fact) {
        if (mapper.insert(fact) != 1) {
            throw new ErrorException("计费事实头插入行数不符: " + fact.getId());
        }
    }

    /** 按主键读取，不存在返回空。 */
    @Override
    public BillingFact findById(long id) {
        return mapper.selectById(id);
    }

    /** 按一组主键读取；空集合不发出 SQL。 */
    @Override
    public List<BillingFact> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectByIds(ids);
    }

    /** 按来源系统与来源事件键读取，不存在返回空。 */
    @Override
    public BillingFact findBySource(String sourceSystem, String sourceEventKey) {
        return mapper.selectBySource(sourceSystem, sourceEventKey);
    }

    /** 替代给定事实的全部事实。 */
    @Override
    public List<BillingFact> findBySupersedesFactId(long supersedesFactId) {
        return mapper.selectBySupersedesFactId(supersedesFactId);
    }

    /** 以给定事实为来源的全部派生事实。 */
    @Override
    public List<BillingFact> findByParentFactId(long parentFactId) {
        return mapper.selectByParentFactId(parentFactId);
    }

    /** DRAFT → APPROVED；影响行数不为 1 时并发冲突，成功后同步状态、版本、复核人、业务时间与批准时间。 */
    @Override
    public void approve(BillingFact fact, long factVersion, long checkerId, Instant now) {
        if (mapper.approve(fact.getId(), factVersion, checkerId, now) != 1) {
            throw new ConcurrentRequestException("计费事实已不是待批准状态: " + fact.getId());
        }
        fact.setStatus(BillingFactStatus.APPROVED);
        fact.setFactVersion(factVersion);
        fact.setCheckerId(checkerId);
        fact.setApprovedTime(now);
        fact.setBusinessTime(now);
        fact.setUpdateTime(now);
    }

    /** APPROVED → SUPERSEDED；影响行数不为 1 时并发冲突，成功后同步状态与时间。 */
    @Override
    public void supersede(BillingFact fact, Instant now) {
        if (mapper.supersede(fact.getId(), fact.getFactVersion(), now) != 1) {
            throw new ConcurrentRequestException("被替代计费事实已被替代或版本变化: " + fact.getId());
        }
        fact.setStatus(BillingFactStatus.SUPERSEDED);
        fact.setUpdateTime(now);
    }

    /** 首次发布确认时写入 published_at；已写入过时不改原对象。 */
    @Override
    public boolean markPublished(BillingFact fact, Instant now) {
        if (mapper.markPublished(fact.getId(), fact.getFactVersion(), now) != 1) {
            return false;
        }
        fact.setPublishedAt(now);
        fact.setUpdateTime(now);
        return true;
    }

    /** 账簿主体下 ID 大于游标的已批准事实。 */
    @Override
    public List<BillingFact> findRecordedByBook(BillingFactBookScope scope, long fromFactId, int limit) {
        return mapper.selectRecordedByBook(scope.getEntityNo(), scope.getFinanceEntityId(), scope.getCurrency(),
            fromFactId, limit);
    }

    /** 账簿主体下已批准事实的发布进度。 */
    @Override
    public BillingFactProgressView publicationProgress(BillingFactBookScope scope) {
        return mapper.selectPublicationProgress(scope.getEntityNo(), scope.getFinanceEntityId(), scope.getCurrency());
    }
}

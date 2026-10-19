package com.paytech.account.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.account.core.dal.dataobject.BalanceAppliedFact;
import com.paytech.account.core.dal.mapper.BalanceAppliedFactMapper;
import com.paytech.account.core.enums.AppliedFactStatus;
import com.paytech.account.core.enums.MaturityStatus;
import com.paytech.account.domainservice.repository.BalanceAppliedFactRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** BalanceAppliedFactRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class BalanceAppliedFactRepositoryImpl implements BalanceAppliedFactRepository {
    private final BalanceAppliedFactMapper mapper;

    /** 唯一键冲突转为可重试的并发冲突。 */
    @Override
    public void insert(BalanceAppliedFact fact) {
        try {
            mapper.insert(fact);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("计费事实被并发登记: " + fact.getFactId() + "/" + fact.getFactVersion(), e);
        }
    }

    /** 以排他锁读取处理记录。 */
    @Override
    public BalanceAppliedFact lockByFactId(long factId) {
        return mapper.selectByFactIdForUpdate(factId);
    }

    /** 空集合不查询。 */
    @Override
    public List<BalanceAppliedFact> findByFactIds(List<Long> factIds) {
        return factIds.isEmpty() ? List.of() : mapper.selectByFactIds(factIds);
    }

    /** 条件迁移，成功后同步原对象。 */
    @Override
    public void updateStatus(BalanceAppliedFact fact, AppliedFactStatus status, Instant now) {
        if (mapper.updateStatus(fact, status, now) != 1) {
            throw new ConcurrentRequestException("计费事实处理状态已变化: " + fact.getFactId());
        }
        fact.setApplyStatus(status);
        fact.setUpdateTime(now);
    }

    /** 经营主体与币种下的进度。 */
    @Override
    public Progress operatingEntityProgress(String entityNo, String currency) {
        return progress(mapper.selectOperatingEntityProgress(entityNo, currency));
    }

    /** Finance Entity 与币种下的进度。 */
    @Override
    public Progress financeEntityProgress(long financeEntityId, String currency) {
        return progress(mapper.selectFinanceEntityProgress(financeEntityId, currency));
    }

    /** 引用 Finance Entity 的经营主体编号。 */
    @Override
    public List<String> findEntityNosByFinanceEntity(long financeEntityId, String currency) {
        return mapper.selectEntityNosByFinanceEntity(financeEntityId, currency);
    }

    /** 到期待移动的事实。 */
    @Override
    public List<BalanceAppliedFact> findMaturityDue(Instant asOf, long afterId, int limit) {
        return mapper.selectMaturityDue(asOf, afterId, limit);
    }

    /** 以 PENDING 为条件标记完成，成功后同步原对象。 */
    @Override
    public void markMatured(BalanceAppliedFact fact, Instant maturedTime) {
        if (mapper.markMatured(fact.getId(), maturedTime) != 1) {
            throw new ConcurrentRequestException("计费事实到期状态已变化: " + fact.getFactId());
        }
        fact.setMaturityStatus(MaturityStatus.MATURED);
        fact.setMaturedTime(maturedTime);
        fact.setUpdateTime(maturedTime);
    }

    /** 聚合行转进度；没有事实时最大值为空。 */
    static Progress progress(Map<String, Object> row) {
        Object max = row.get("maxFactId");
        return new Progress(((Number)row.get("factCount")).longValue(), max == null ? null : ((Number)max).longValue());
    }
}

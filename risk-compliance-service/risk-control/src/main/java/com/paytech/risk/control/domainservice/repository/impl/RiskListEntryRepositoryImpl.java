package com.paytech.risk.control.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.risk.control.core.dal.dataobject.RiskListEntry;
import com.paytech.risk.control.core.dal.mapper.RiskListEntryMapper;
import com.paytech.risk.control.core.enums.RiskListEntryStatus;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.control.domainservice.repository.RiskListEntryRepository;

import lombok.AllArgsConstructor;

/** 风险名单成员持久化。 */
@Repository
@AllArgsConstructor
public class RiskListEntryRepositoryImpl implements RiskListEntryRepository {
    private final RiskListEntryMapper mapper;

    /** 插入成员；生效槽位冲突转为并发冲突。 */
    @Override
    public void insert(RiskListEntry row) {
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            throw new ConcurrentRequestException("名单中已有该主体的生效成员");
        }
    }

    /** 按 ID 读取成员。 */
    @Override
    public Optional<RiskListEntry> findById(long entryId) {
        return Optional.ofNullable(mapper.selectById(entryId));
    }

    /** 读取同名单同主体当前占用生效槽位的成员。 */
    @Override
    public Optional<RiskListEntry> findActive(long listId, RiskSubjectType subjectType, String subjectKey) {
        return Optional.ofNullable(mapper.selectActive(listId, subjectType, subjectKey));
    }

    /** 条件更新成功后同步原对象；行数不符说明成员已被并发变更。 */
    @Override
    public void close(RiskListEntry row, RiskListEntryStatus status, Long deactivatedBy, String deactivatedReason,
        Instant effectiveTo, Instant now) {
        if (mapper.close(row, status, deactivatedBy, deactivatedReason, effectiveTo, now) != 1) {
            throw new ConcurrentRequestException("名单成员已变更: " + row.getEntryId());
        }
        row.setStatus(status);
        row.setActiveSlot(null);
        row.setDeactivatedBy(deactivatedBy);
        row.setDeactivatedReason(deactivatedReason);
        row.setEffectiveTo(effectiveTo);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
    }

    /** 指定时刻主体是否处于名单成员的生效区间内。 */
    @Override
    public boolean isEffective(long listId, RiskSubjectType subjectType, String subjectKey, Instant at) {
        return mapper.countEffective(listId, subjectType, subjectKey, at) > 0;
    }

    /** 按条件分页读取成员。 */
    @Override
    public List<RiskListEntry> findPage(long listId, String subjectKey, RiskListEntryStatus status, int limit,
        long offset) {
        return mapper.selectPage(listId, subjectKey, status, limit, offset);
    }

    /** 统计同一条件下的成员数量。 */
    @Override
    public long count(long listId, String subjectKey, RiskListEntryStatus status) {
        return mapper.count(listId, subjectKey, status);
    }
}

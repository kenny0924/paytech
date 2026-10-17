package com.paytech.risk.decision.domainservice.repository.impl;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.core.dal.mapper.ExecutionReleaseMapper;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domainservice.repository.ExecutionReleaseRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 执行发布持久化。 */
@Repository
@AllArgsConstructor
public class ExecutionReleaseRepositoryImpl implements ExecutionReleaseRepository {
    private final ExecutionReleaseMapper mapper;

    /** 插入发布。 */
    @Override
    public void insert(ExecutionRelease row) {
        mapper.insert(row);
    }

    /** 按 ID 读取执行发布。 */
    @Override
    public Optional<ExecutionRelease> findById(long releaseId) {
        return Optional.ofNullable(mapper.selectById(releaseId));
    }

    /** 锁定执行发布。 */
    @Override
    public Optional<ExecutionRelease> lockById(long releaseId) {
        return Optional.ofNullable(mapper.lockById(releaseId));
    }

    /** 读取决策点在环境中的生效发布。 */
    @Override
    public Optional<ExecutionRelease> findActive(long decisionPointId, DecisionEnvironment environment) {
        return Optional.ofNullable(mapper.selectActive(decisionPointId, environment));
    }

    /** 锁定决策点在环境中的生效发布。 */
    @Override
    public Optional<ExecutionRelease> lockActive(long decisionPointId, DecisionEnvironment environment) {
        return Optional.ofNullable(mapper.lockActive(decisionPointId, environment));
    }

    /** 条件更新成功后同步原对象。 */
    @Override
    public void transition(ExecutionRelease row, DecisionReleaseStatus status, Boolean activeSlot, Instant activatedTime, Instant supersededTime, Instant now) {
        int updated;
        try {
            updated = mapper.transition(row, status, activeSlot, activatedTime, supersededTime, now);
        } catch (DuplicateKeyException duplicate) {
            throw new ConcurrentRequestException("决策点已有生效发布: " + row.getDecisionPointId());
        }
        if (updated != 1) {
            throw new ConcurrentRequestException("发布状态已变更: " + row.getReleaseId());
        }
        row.setStatus(status);
        row.setActiveSlot(activeSlot);
        row.setActivatedTime(activatedTime);
        row.setSupersededTime(supersededTime);
        row.setVersion(row.getVersion() + 1);
        row.setUpdateTime(now);
    }
}

package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import java.time.Instant;
import java.util.Optional;

/** 执行发布的单表持久化边界。 */
public interface ExecutionReleaseRepository {
    /** 插入发布。 */
    void insert(ExecutionRelease row);

    /** 按 ID 读取执行发布。 */
    Optional<ExecutionRelease> findById(long releaseId);

    /** 锁定执行发布。 */
    Optional<ExecutionRelease> lockById(long releaseId);

    /** 读取决策点在环境中的生效发布。 */
    Optional<ExecutionRelease> findActive(long decisionPointId, DecisionEnvironment environment);

    /** 锁定决策点在环境中的生效发布。 */
    Optional<ExecutionRelease> lockActive(long decisionPointId, DecisionEnvironment environment);

    /**
     * 推进发布状态
     * <li>按原状态与版本条件更新；成功后同步原对象，生效槽位冲突或行数不符抛出并发冲突。</li>
     */
    void transition(ExecutionRelease row, DecisionReleaseStatus status, Boolean activeSlot, Instant activatedTime, Instant supersededTime, Instant now);
}

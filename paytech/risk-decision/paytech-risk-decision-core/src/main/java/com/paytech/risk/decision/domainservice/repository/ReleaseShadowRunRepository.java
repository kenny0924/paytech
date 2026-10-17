package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.ReleaseShadowRun;
import java.util.Optional;

/** 影子批次的单表持久化边界。 */
public interface ReleaseShadowRunRepository {
    /** 插入批次。 */
    void insert(ReleaseShadowRun row);

    /** 按 ID 读取。 */
    Optional<ReleaseShadowRun> findById(long shadowRunId);

    /** 读取候选发布最近的批次。 */
    Optional<ReleaseShadowRun> findLatestByCandidate(long candidateReleaseId);

    /**
     * 写入完成汇总
     * <li>row 已设置汇总字段，expectedVersion 为读取时版本；成功后同步版本，批次已结束或版本变化时抛出并发冲突。</li>
     */
    void complete(ReleaseShadowRun row, long expectedVersion);
}

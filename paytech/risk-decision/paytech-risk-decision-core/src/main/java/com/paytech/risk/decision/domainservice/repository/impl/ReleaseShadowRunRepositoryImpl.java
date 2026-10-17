package com.paytech.risk.decision.domainservice.repository.impl;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseShadowRun;
import com.paytech.risk.decision.core.dal.mapper.ReleaseShadowRunMapper;
import com.paytech.risk.decision.domainservice.repository.ReleaseShadowRunRepository;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

/** 影子批次持久化。 */
@Repository
@AllArgsConstructor
public class ReleaseShadowRunRepositoryImpl implements ReleaseShadowRunRepository {
    private final ReleaseShadowRunMapper mapper;

    /** 插入批次。 */
    @Override
    public void insert(ReleaseShadowRun row) {
        mapper.insert(row);
    }

    /** 按 ID 读取。 */
    @Override
    public Optional<ReleaseShadowRun> findById(long shadowRunId) {
        return Optional.ofNullable(mapper.selectById(shadowRunId));
    }

    /** 读取候选发布最近的批次。 */
    @Override
    public Optional<ReleaseShadowRun> findLatestByCandidate(long candidateReleaseId) {
        return Optional.ofNullable(mapper.selectLatestByCandidate(candidateReleaseId));
    }

    /** 条件更新成功后同步版本。 */
    @Override
    public void complete(ReleaseShadowRun row, long expectedVersion) {
        if (mapper.complete(row, expectedVersion) != 1) {
            throw new ConcurrentRequestException("影子批次已结束或已变更: " + row.getShadowRunId());
        }
        row.setVersion(expectedVersion + 1);
    }
}

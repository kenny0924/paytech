package com.paytech.risk.decision.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.risk.decision.core.dal.dataobject.ExecutionReleaseSummary;
import com.paytech.risk.decision.core.dal.mapper.ExecutionReleaseQueryMapper;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domainservice.repository.ExecutionReleaseQueryRepository;

import lombok.AllArgsConstructor;

/** 执行发布列表查询。 */
@Repository
@AllArgsConstructor
public class ExecutionReleaseQueryRepositoryImpl implements ExecutionReleaseQueryRepository {
    private final ExecutionReleaseQueryMapper mapper;

    /** 按决策点与可选状态分页读取。 */
    @Override
    public List<ExecutionReleaseSummary> findPage(long decisionPointId, DecisionReleaseStatus status, int limit, int offset) {
        return mapper.selectPage(decisionPointId, status, limit, offset);
    }

    /** 与分页相同筛选的总数。 */
    @Override
    public long count(long decisionPointId, DecisionReleaseStatus status) {
        return mapper.count(decisionPointId, status);
    }
}

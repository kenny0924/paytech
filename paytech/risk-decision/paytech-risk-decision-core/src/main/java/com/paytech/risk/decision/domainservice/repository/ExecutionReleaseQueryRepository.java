package com.paytech.risk.decision.domainservice.repository;

import java.util.List;

import com.paytech.risk.decision.core.dal.dataobject.ExecutionReleaseSummary;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;

/** 执行发布列表的只读查询边界。 */
public interface ExecutionReleaseQueryRepository {
    /** 按决策点与可选状态分页读取，创建时间、发布 ID 倒序。 */
    List<ExecutionReleaseSummary> findPage(long decisionPointId, DecisionReleaseStatus status, int limit, int offset);

    /** 与分页相同筛选的总数。 */
    long count(long decisionPointId, DecisionReleaseStatus status);
}

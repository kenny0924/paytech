package com.paytech.risk.decision.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.decision.core.dal.dataobject.ExecutionReleaseSummary;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;

/** 执行发布列表的分页查询；只读执行发布（决策流版本非空），不含结账测试候选。 */
public interface ExecutionReleaseQueryMapper {
    /** 按决策点与可选状态分页读取，创建时间、发布 ID 倒序。 */
    List<ExecutionReleaseSummary> selectPage(@Param("decisionPointId") long decisionPointId,
        @Param("status") DecisionReleaseStatus status, @Param("limit") int limit, @Param("offset") int offset);

    /** 与分页相同筛选的总数。 */
    long count(@Param("decisionPointId") long decisionPointId, @Param("status") DecisionReleaseStatus status);
}

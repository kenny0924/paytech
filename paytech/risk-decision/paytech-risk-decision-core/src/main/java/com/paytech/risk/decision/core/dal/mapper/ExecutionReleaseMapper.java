package com.paytech.risk.decision.core.dal.mapper;

import com.paytech.risk.decision.core.dal.dataobject.ExecutionRelease;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** 冻结决策流的执行发布；与结账测试候选共用发布表，以决策流版本非空区分表映射。 */
public interface ExecutionReleaseMapper {
    /** 插入一行。 */
    int insert(@Param("row") ExecutionRelease row);

    /** 按 ID 读取执行发布；结账测试候选不返回。 */
    ExecutionRelease selectById(@Param("releaseId") long releaseId);

    /** 锁定执行发布行。 */
    ExecutionRelease lockById(@Param("releaseId") long releaseId);

    /** 读取决策点在环境中的生效发布。 */
    ExecutionRelease selectActive(@Param("decisionPointId") long decisionPointId, @Param("environment") DecisionEnvironment environment);

    /** 锁定决策点在环境中的生效发布。 */
    ExecutionRelease lockActive(@Param("decisionPointId") long decisionPointId, @Param("environment") DecisionEnvironment environment);

    /** 按期望状态与版本推进状态及激活字段。 */
    int transition(@Param("row") ExecutionRelease row, @Param("status") DecisionReleaseStatus status, @Param("activeSlot") Boolean activeSlot, @Param("activatedTime") Instant activatedTime, @Param("supersededTime") Instant supersededTime, @Param("now") Instant now);
}

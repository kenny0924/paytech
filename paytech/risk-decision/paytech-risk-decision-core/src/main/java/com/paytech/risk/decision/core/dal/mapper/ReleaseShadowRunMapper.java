package com.paytech.risk.decision.core.dal.mapper;

import com.paytech.risk.decision.core.dal.dataobject.ReleaseShadowRun;
import org.apache.ibatis.annotations.Param;

/** 候选发布回放基线评估的影子批次表映射。 */
public interface ReleaseShadowRunMapper {
    /** 插入一行。 */
    int insert(@Param("row") ReleaseShadowRun row);

    /** 按 ID 读取。 */
    ReleaseShadowRun selectById(@Param("shadowRunId") long shadowRunId);

    /** 读取候选发布最近的批次。 */
    ReleaseShadowRun selectLatestByCandidate(@Param("candidateReleaseId") long candidateReleaseId);

    /** 以期望状态与版本写入完成汇总。 */
    int complete(@Param("row") ReleaseShadowRun row, @Param("expectedVersion") long expectedVersion);
}

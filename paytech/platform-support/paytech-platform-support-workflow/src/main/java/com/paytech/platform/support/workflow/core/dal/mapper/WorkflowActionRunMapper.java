package com.paytech.platform.support.workflow.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;

/** t_workflow_action_run 的 MyBatis 映射，SQL 位于 mapper/workflow/WorkflowActionRunMapper.xml。 */
public interface WorkflowActionRunMapper {

    /** 插入一行。 */
    int insert(@Param("row") WorkflowActionRun row);

    /** 按主键读取。 */
    WorkflowActionRun selectById(@Param("id") long id);

    /** 见 XML 同名语句。 */
    List<WorkflowActionRun> selectByInstance(@Param("instanceId") long instanceId);

    /** 见 XML 同名语句。 */
    int update(@Param("row") WorkflowActionRun row);

    /**
     * 本分片到期待检查的 DATA_QUERY 动作运行。
     *
     * @param shardIndex 分片序号（按 instance_id 取模）
     * @param shardTotal 分片总数
     * @param checkedBefore 最近检查早于此时间的才到期；从未检查的总是到期
     * @param limit 最多条数
     * @return 动作运行，从未检查的在前
     */
    List<WorkflowActionRun> selectDueDataQueryChecks(@Param("shardIndex") int shardIndex,
        @Param("shardTotal") int shardTotal, @Param("checkedBefore") Instant checkedBefore, @Param("limit") int limit);

    /**
     * 动作输出引用了该文件地址的其他实例，最多一个。
     *
     * @param fileAddress 已按转义符 ! 转义 LIKE 通配符的文件地址
     * @param excludeInstanceId 不计入的实例；可空
     * @return 实例 ID
     */
    List<Long> selectInstanceIdsReferencingFile(@Param("fileAddress") String fileAddress,
        @Param("excludeInstanceId") Long excludeInstanceId);
}

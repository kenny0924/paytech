package com.paytech.platform.support.workflow.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowNodeRun;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;

/** t_workflow_node_run 的 MyBatis 映射，SQL 位于 mapper/workflow/WorkflowNodeRunMapper.xml。 */
public interface WorkflowNodeRunMapper {

    /** 插入一行。 */
    int insert(@Param("row") WorkflowNodeRun row);

    /** 按主键读取。 */
    WorkflowNodeRun selectById(@Param("id") long id);

    /** 见 XML 同名语句。 */
    List<WorkflowNodeRun> selectByInstance(@Param("instanceId") long instanceId);

    /** 见 XML 同名语句。 */
    int updateStatus(@Param("nodeRunId") long nodeRunId, @Param("from") NodeRunStatus from, @Param("to") NodeRunStatus to, @Param("completedTime") Instant completedTime, @Param("now") Instant now);
}

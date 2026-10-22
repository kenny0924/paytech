package com.paytech.platform.support.workflow.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowNodeRun;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;

/** 节点运行（t_workflow_node_run）的单表访问。 */
public interface WorkflowNodeRunRepository {

    /** 插入新节点运行。 */
    void insert(WorkflowNodeRun row);

    /** 实例的全部节点运行，按激活时间。 */
    List<WorkflowNodeRun> findByInstance(long instanceId);

    /** 以期望旧状态条件更新状态；行数不符为并发冲突。 */
    void updateStatus(long nodeRunId, NodeRunStatus from, NodeRunStatus to, Instant completedTime, Instant now);
}

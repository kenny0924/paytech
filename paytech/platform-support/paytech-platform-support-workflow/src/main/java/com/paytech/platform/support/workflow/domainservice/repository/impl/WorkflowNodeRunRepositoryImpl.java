package com.paytech.platform.support.workflow.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowNodeRun;
import com.paytech.platform.support.workflow.core.dal.mapper.WorkflowNodeRunMapper;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowNodeRunRepository;

import lombok.AllArgsConstructor;

/** 节点运行单表访问实现；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class WorkflowNodeRunRepositoryImpl implements WorkflowNodeRunRepository {

    private final WorkflowNodeRunMapper mapper;

    /** 插入新节点运行。 */
    @Override
    public void insert(WorkflowNodeRun row) {
        mapper.insert(row);
    }

    /** 实例的全部节点运行，按激活时间。 */
    @Override
    public List<WorkflowNodeRun> findByInstance(long instanceId) {
        return mapper.selectByInstance(instanceId);
    }

    /** 以期望旧状态条件更新状态；行数不符为并发冲突。 */
    @Override
    public void updateStatus(long nodeRunId, NodeRunStatus from, NodeRunStatus to, Instant completedTime, Instant now) {
        if (mapper.updateStatus(nodeRunId, from, to, completedTime, now) != 1) {
            throw new ConcurrentRequestException("节点状态已变化，请刷新后重试");
        }
    }
}

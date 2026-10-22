package com.paytech.platform.support.workflow.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.dal.mapper.WorkflowActionRunMapper;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowActionRunRepository;

import lombok.AllArgsConstructor;

/** 动作运行单表访问实现；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class WorkflowActionRunRepositoryImpl implements WorkflowActionRunRepository {

    private final WorkflowActionRunMapper mapper;

    /** 插入新动作运行。 */
    @Override
    public void insert(WorkflowActionRun row) {
        mapper.insert(row);
    }

    /** 按主键读取；不存在为 null。 */
    @Override
    public WorkflowActionRun find(long actionRunId) {
        return mapper.selectById(actionRunId);
    }

    /** 实例的全部动作运行。 */
    @Override
    public List<WorkflowActionRun> findByInstance(long instanceId) {
        return mapper.selectByInstance(instanceId);
    }

    /** 按原版本保存执行人、状态与提交内容；版本不符为并发冲突。 */
    @Override
    public void update(WorkflowActionRun row) {
        if (mapper.update(row) != 1) {
            throw new ConcurrentRequestException("动作已被其他操作修改，请刷新后重试");
        }
        row.setVersion(row.getVersion() + 1);
    }

    /** 按第二层数组元素精确匹配文件地址（转义 LIKE 通配符）。 */
    @Override
    public boolean referencedByOtherInstance(String fileAddress, Long excludeInstanceId) {
        return !mapper.selectInstanceIdsReferencingFile(WorkflowValueFormats.likeLiteral(fileAddress), excludeInstanceId).isEmpty();
    }
}

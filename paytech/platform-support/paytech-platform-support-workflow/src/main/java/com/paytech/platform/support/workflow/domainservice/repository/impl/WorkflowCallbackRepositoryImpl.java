package com.paytech.platform.support.workflow.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowCallback;
import com.paytech.platform.support.workflow.core.dal.mapper.WorkflowCallbackMapper;
import com.paytech.platform.support.workflow.core.enums.CallbackStatus;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowCallbackRepository;

import lombok.AllArgsConstructor;

/** 通知与回调单表访问实现；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class WorkflowCallbackRepositoryImpl implements WorkflowCallbackRepository {

    private final WorkflowCallbackMapper mapper;

    /** 插入新记录。 */
    @Override
    public void insert(WorkflowCallback row) {
        mapper.insert(row);
    }

    /** 按主键读取；不存在为 null。 */
    @Override
    public WorkflowCallback find(long callbackId) {
        return mapper.selectById(callbackId);
    }

    /** 实例的全部通知与回调。 */
    @Override
    public List<WorkflowCallback> findByInstance(long instanceId) {
        return mapper.selectByInstance(instanceId);
    }

    /** 以原版本与期望旧状态保存投递结果；不符为并发冲突。 */
    @Override
    public void update(WorkflowCallback row, CallbackStatus expected) {
        if (mapper.update(row, expected) != 1) {
            throw new ConcurrentRequestException("通知或回调状态已变化，请刷新后重试");
        }
        row.setVersion(row.getVersion() + 1);
    }
}

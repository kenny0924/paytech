package com.paytech.platform.support.workflow.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowCallback;
import com.paytech.platform.support.workflow.core.enums.CallbackStatus;

/** 通知与回调（t_workflow_callback）的单表访问。 */
public interface WorkflowCallbackRepository {

    /** 插入新记录。 */
    void insert(WorkflowCallback row);

    /** 按主键读取；不存在为 null。 */
    WorkflowCallback find(long callbackId);

    /** 实例的全部通知与回调。 */
    List<WorkflowCallback> findByInstance(long instanceId);

    /** 以原版本与期望旧状态保存投递结果；不符为并发冲突。 */
    void update(WorkflowCallback row, CallbackStatus expected);
}

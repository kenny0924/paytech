package com.paytech.platform.support.workflow.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowApproval;

/** 审批（t_workflow_approval）的单表访问。 */
public interface WorkflowApprovalRepository {

    /** 插入新审批行。 */
    void insert(WorkflowApproval row);

    /** 按主键读取；不存在为 null。 */
    WorkflowApproval find(long approvalId);

    /** 实例的全部审批行。 */
    List<WorkflowApproval> findByInstance(long instanceId);

    /** 按原版本保存审批人与决定；版本不符为并发冲突。 */
    void update(WorkflowApproval row);
}

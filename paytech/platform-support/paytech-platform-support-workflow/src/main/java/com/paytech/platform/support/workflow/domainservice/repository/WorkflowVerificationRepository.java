package com.paytech.platform.support.workflow.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowVerification;

/** 验证记录（t_workflow_verification）的单表访问。 */
public interface WorkflowVerificationRepository {

    /** 追加一条验证记录。 */
    void insert(WorkflowVerification row);

    /** 实例的全部验证记录，按验证时间。 */
    List<WorkflowVerification> findByInstance(long instanceId);
}

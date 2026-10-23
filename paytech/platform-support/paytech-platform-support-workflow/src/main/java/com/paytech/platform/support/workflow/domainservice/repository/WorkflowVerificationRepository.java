package com.paytech.platform.support.workflow.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowVerification;

/** 历史验证记录（t_workflow_verification）的单表只读访问；2026-10-22 去掉动作验证后不再写入。 */
public interface WorkflowVerificationRepository {

    /** 实例的全部验证记录，按验证时间。 */
    List<WorkflowVerification> findByInstance(long instanceId);
}

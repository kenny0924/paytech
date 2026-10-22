package com.paytech.platform.support.workflow.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowTransfer;

/** 转交记录（t_workflow_transfer）的单表访问。 */
public interface WorkflowTransferRepository {

    /** 追加一条转交记录。 */
    void insert(WorkflowTransfer row);

    /** 实例的全部转交记录。 */
    List<WorkflowTransfer> findByInstance(long instanceId);
}

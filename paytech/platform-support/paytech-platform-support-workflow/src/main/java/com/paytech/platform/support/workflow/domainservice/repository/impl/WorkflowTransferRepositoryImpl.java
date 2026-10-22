package com.paytech.platform.support.workflow.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowTransfer;
import com.paytech.platform.support.workflow.core.dal.mapper.WorkflowTransferMapper;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowTransferRepository;

import lombok.AllArgsConstructor;

/** 转交记录单表访问实现；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class WorkflowTransferRepositoryImpl implements WorkflowTransferRepository {

    private final WorkflowTransferMapper mapper;

    /** 追加一条转交记录。 */
    @Override
    public void insert(WorkflowTransfer row) {
        mapper.insert(row);
    }

    /** 实例的全部转交记录。 */
    @Override
    public List<WorkflowTransfer> findByInstance(long instanceId) {
        return mapper.selectByInstance(instanceId);
    }
}

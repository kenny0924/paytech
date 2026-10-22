package com.paytech.platform.support.workflow.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowVerification;
import com.paytech.platform.support.workflow.core.dal.mapper.WorkflowVerificationMapper;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowVerificationRepository;

import lombok.AllArgsConstructor;

/** 验证记录单表访问实现；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class WorkflowVerificationRepositoryImpl implements WorkflowVerificationRepository {

    private final WorkflowVerificationMapper mapper;

    /** 追加一条验证记录。 */
    @Override
    public void insert(WorkflowVerification row) {
        mapper.insert(row);
    }

    /** 实例的全部验证记录，按验证时间。 */
    @Override
    public List<WorkflowVerification> findByInstance(long instanceId) {
        return mapper.selectByInstance(instanceId);
    }
}

package com.paytech.platform.support.workflow.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowVerification;
import com.paytech.platform.support.workflow.core.dal.mapper.WorkflowVerificationMapper;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowVerificationRepository;

import lombok.AllArgsConstructor;

/** 历史验证记录单表只读访问实现（2026-10-22 起不再写入）。 */
@Repository
@AllArgsConstructor
public class WorkflowVerificationRepositoryImpl implements WorkflowVerificationRepository {

    private final WorkflowVerificationMapper mapper;

    /** 实例的全部验证记录，按验证时间。 */
    @Override
    public List<WorkflowVerification> findByInstance(long instanceId) {
        return mapper.selectByInstance(instanceId);
    }
}

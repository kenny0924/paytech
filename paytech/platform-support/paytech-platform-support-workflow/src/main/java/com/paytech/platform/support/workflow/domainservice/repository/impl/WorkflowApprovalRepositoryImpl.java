package com.paytech.platform.support.workflow.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowApproval;
import com.paytech.platform.support.workflow.core.dal.mapper.WorkflowApprovalMapper;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowApprovalRepository;

import lombok.AllArgsConstructor;

/** 审批单表访问实现；条件更新成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class WorkflowApprovalRepositoryImpl implements WorkflowApprovalRepository {

    private final WorkflowApprovalMapper mapper;

    /** 插入新审批行。 */
    @Override
    public void insert(WorkflowApproval row) {
        mapper.insert(row);
    }

    /** 按主键读取；不存在为 null。 */
    @Override
    public WorkflowApproval find(long approvalId) {
        return mapper.selectById(approvalId);
    }

    /** 实例的全部审批行。 */
    @Override
    public List<WorkflowApproval> findByInstance(long instanceId) {
        return mapper.selectByInstance(instanceId);
    }

    /** 按原版本保存审批人与决定；版本不符为并发冲突。 */
    @Override
    public void update(WorkflowApproval row) {
        if (mapper.update(row) != 1) {
            throw new ConcurrentRequestException("审批已被其他操作处理，请刷新后重试");
        }
        row.setVersion(row.getVersion() + 1);
    }
}

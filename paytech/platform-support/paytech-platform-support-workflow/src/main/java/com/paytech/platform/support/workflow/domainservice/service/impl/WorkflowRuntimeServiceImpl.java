package com.paytech.platform.support.workflow.domainservice.service.impl;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.domain.WorkflowRuntimeChange;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowRuntimeService;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowActionRunRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowApprovalRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowCallbackRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowInstanceRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowNodeRunRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowTransferRepository;

import lombok.AllArgsConstructor;

/**
 * 运行变化的保存顺序：实例（插入或版本 CAS）→ 节点运行（旧状态条件更新后插入新运行）→ 动作运行 → 审批行 →
 * 验证与转交记录 → 通知回调 → 补偿任务登记。全部只写本库，不调用外部服务。
 */
@Service
@AllArgsConstructor
public class WorkflowRuntimeServiceImpl implements WorkflowRuntimeService {

    private final ObjectProvider<CompensationEventRegistrar> registrar;
    private final WorkflowNodeRunRepository nodeRuns;
    private final WorkflowInstanceRepository instances;
    private final WorkflowApprovalRepository approvals;
    private final WorkflowCallbackRepository callbacks;
    private final WorkflowTransferRepository transfers;
    private final WorkflowActionRunRepository actionRuns;

    /** 按固定顺序保存全部变化。 */
    @Override
    public void apply(WorkflowRuntimeChange change) {
        if (change.isNewInstance()) {
            instances.insert(change.getInstance());
        } else {
            instances.update(change.getInstance());
        }
        change.getNodeRunUpdates().forEach(update -> nodeRuns.updateStatus(update.row().getNodeRunId(), update.from(),
            update.to(), update.row().getCompletedTime(), update.row().getUpdateTime()));
        change.getNewNodeRuns().forEach(nodeRuns::insert);
        change.getUpdatedActionRuns().forEach(actionRuns::update);
        change.getNewActionRuns().forEach(actionRuns::insert);
        change.getUpdatedApprovals().forEach(approvals::update);
        change.getNewApprovals().forEach(approvals::insert);
        change.getNewTransfers().forEach(transfers::insert);
        change.getNewCallbacks().forEach(callbacks::insert);
        change.getCompensationEvents().forEach(event -> registrar().register(event));
    }

    /** 只按动作版本保存检查列。 */
    @Override
    public void recordDataQueryCheck(WorkflowActionRun row) {
        actionRuns.update(row);
    }

    /**
     * 补偿登记器；通知回调必须可靠投递，未开启 compensation.enabled 时明确失败并回滚。
     *
     * @return 登记器
     */
    private CompensationEventRegistrar registrar() {
        CompensationEventRegistrar available = registrar.getIfAvailable();
        if (available == null) {
            throw new ErrorException("工作流通知与回调需要开启 compensation.enabled");
        }
        return available;
    }
}

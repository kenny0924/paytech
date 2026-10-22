package com.paytech.platform.support.workflow.domainservice.service.impl;

import java.time.Instant;
import java.util.ArrayList;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowInstance;
import com.paytech.platform.support.workflow.domain.WorkflowInstanceContext;
import com.paytech.platform.support.workflow.domainservice.convert.FormDefinitionJsonReader;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowGraphJsonReader;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowRuntimeJson;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowActionRunRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowApprovalRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowCallbackRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionVersionRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowInstanceRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowNodeRunRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowTransferRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowVerificationRepository;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowInstanceQueryService;

import lombok.RequiredArgsConstructor;

/** 一次读取实例、版本与全部运行记录，并解析版本冻结的流程图、启动表单、模板快照与人员分配。 */
@Service
@RequiredArgsConstructor
public class WorkflowInstanceQueryServiceImpl implements WorkflowInstanceQueryService {

    private final WorkflowNodeRunRepository nodeRuns;
    private final WorkflowInstanceRepository instances;
    private final WorkflowApprovalRepository approvals;
    private final WorkflowCallbackRepository callbacks;
    private final WorkflowTransferRepository transfers;
    private final WorkflowActionRunRepository actionRuns;
    private final WorkflowDefinitionRepository definitions;
    private final WorkflowVerificationRepository verifications;
    private final WorkflowDefinitionVersionRepository versions;
    private final WorkflowRuntimeJson json = new WorkflowRuntimeJson();
    private final WorkflowGraphJsonReader graphReader = new WorkflowGraphJsonReader();
    private final FormDefinitionJsonReader formReader = new FormDefinitionJsonReader();

    /** 读取并解析；实例存在而版本缺失属于数据不一致。 */
    @Override
    public WorkflowInstanceContext load(long instanceId, Instant now) {
        WorkflowInstance instance = instances.find(instanceId);
        if (instance == null) {
            return null;
        }
        WorkflowDefinitionVersion version = versions.find(instance.getDefinitionVersionId());
        if (version == null || version.getActionSnapshotJson() == null) {
            throw new ErrorException("实例绑定的流程版本不存在或未发布：" + instanceId);
        }
        var snapshot = new WorkflowInstanceContext.Snapshot(definitions.find(instance.getDefinitionId()), version,
            instance, graphReader.read(version.getGraphJson()), formReader.read(version.getStartFormJson()),
            json.readSnapshot(version.getActionSnapshotJson()), json.readAssignment(instance.getAssignmentJson()));
        var runs = new WorkflowInstanceContext.Runs(new ArrayList<>(nodeRuns.findByInstance(instanceId)),
            new ArrayList<>(actionRuns.findByInstance(instanceId)), new ArrayList<>(approvals.findByInstance(instanceId)),
            new ArrayList<>(verifications.findByInstance(instanceId)), new ArrayList<>(transfers.findByInstance(instanceId)),
            new ArrayList<>(callbacks.findByInstance(instanceId)));
        return new WorkflowInstanceContext(snapshot, runs, now, false);
    }
}

package com.paytech.platform.support.workflow.domainservice.service.impl;

import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowCallback;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowInstance;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowNodeRun;
import com.paytech.platform.support.workflow.core.enums.CallbackStatus;
import com.paytech.platform.support.workflow.domain.WorkflowCallbackDelivery;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowGraphJsonReader;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowRuntimeJson;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowCallbackRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionVersionRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowInstanceRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowNodeRunRepository;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowCallbackService;

import lombok.RequiredArgsConstructor;

/** 通知回调的读取与结果回写；回写以原版本与期望旧状态为条件。 */
@Service
@RequiredArgsConstructor
public class WorkflowCallbackServiceImpl implements WorkflowCallbackService {

    /** 错误摘要列上限。 */
    private static final int ERROR_LIMIT = 512;

    private final ObjectProvider<CompensationEventRegistrar> registrar;
    private final WorkflowNodeRunRepository nodeRuns;
    private final WorkflowInstanceRepository instances;
    private final WorkflowCallbackRepository callbacks;
    private final WorkflowDefinitionRepository definitions;
    private final WorkflowDefinitionVersionRepository versions;
    private final WorkflowRuntimeJson json = new WorkflowRuntimeJson();
    private final WorkflowGraphJsonReader graphReader = new WorkflowGraphJsonReader();

    /** 读取记录、实例、节点运行与版本；不存在返回 null。 */
    @Override
    public WorkflowCallbackDelivery load(long callbackId) {
        WorkflowCallback callback = callbacks.find(callbackId);
        if (callback == null) {
            return null;
        }
        WorkflowInstance instance = instances.find(callback.getInstanceId());
        WorkflowDefinitionVersion version = versions.find(instance.getDefinitionVersionId());
        WorkflowNodeRun nodeRun = callback.getNodeRunId() == null ? null : nodeRuns.findByInstance(
            instance.getInstanceId()).stream().filter(run -> run.getNodeRunId().equals(callback.getNodeRunId()))
            .findFirst().orElse(null);
        String nodeName = nodeRun == null ? null : graphReader.read(version.getGraphJson()).node(nodeRun.getNodeKey())
            .map(node -> node.name()).orElse(nodeRun.getNodeKey());
        return new WorkflowCallbackDelivery(callback, json.readTarget(callback.getTarget()), instance, nodeRun, nodeName,
            definitions.find(instance.getDefinitionId()), version);
    }

    /** PENDING → SENT，计入本次尝试。 */
    @Override
    public void markSent(WorkflowCallback callback, Instant now) {
        callback.setStatus(CallbackStatus.SENT);
        callback.setAttemptCount(callback.getAttemptCount() + 1);
        callback.setLastError(null);
        callback.setSentTime(now);
        callback.setUpdateTime(now);
        callbacks.update(callback, CallbackStatus.PENDING);
    }

    /** 计入本次失败；达到上限置 FAILED。 */
    @Override
    public boolean markAttemptFailed(WorkflowCallback callback, String error, int maxAttempts, Instant now) {
        int attempts = callback.getAttemptCount() + 1;
        callback.setAttemptCount(attempts);
        callback.setLastError(error.length() <= ERROR_LIMIT ? error : error.substring(0, ERROR_LIMIT));
        callback.setStatus(attempts >= maxAttempts ? CallbackStatus.FAILED : CallbackStatus.PENDING);
        callback.setUpdateTime(now);
        callbacks.update(callback, CallbackStatus.PENDING);
        return callback.getStatus() == CallbackStatus.FAILED;
    }

    /** FAILED → PENDING 并登记新的补偿任务。 */
    @Override
    public void reopen(WorkflowCallback callback, CompensationEventRequest event, Instant now) {
        callback.setStatus(CallbackStatus.PENDING);
        callback.setAttemptCount(0);
        callback.setUpdateTime(now);
        callbacks.update(callback, CallbackStatus.FAILED);
        registrar().register(event);
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

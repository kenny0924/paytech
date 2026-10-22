package com.paytech.platform.support.workflow.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowCallback;
import com.paytech.platform.support.workflow.core.enums.CallbackStatus;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowCallbackRepository;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowCallbackService;
import com.paytech.platform.support.workflow.service.processor.request.RetryCallbackRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowMutation;

import lombok.AllArgsConstructor;

/**
 * 手动重试 FAILED 的通知或回调（AC-18）：需 WORKFLOW_INSTANCE_MANAGE；记录回到 PENDING、清零尝试次数并在同一事务登记新的补偿任务，
 * 提交后由对应 Handler 重新投递
 * <li>锁 workflow-instance:{id}（TRY_LOCK），记录以原版本与 FAILED 状态为条件更新。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class RetryWorkflowCallbackProcessor extends AbstractLockProcessor<RetryCallbackRequest, WorkflowMutation> {

    private final WorkflowProcessorSupport support;
    private final WorkflowCallbackService service;
    private final WorkflowCallbackRepository callbacks;

    /** 按所属实例加锁。 */
    @Override
    protected LockCommand lockCommand(RetryCallbackRequest request) {
        return LockCommand.tryLock(WorkflowInstanceCommands.lock(callback(request.getCallbackId()).getInstanceId()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected WorkflowMutation processWithinLock(RetryCallbackRequest request) {
        return support.write(() -> {
            var session = support.authorize(request.getActor(), request.getSession(), WorkflowPermissions.INSTANCE_MANAGE);
            WorkflowCallback callback = callback(request.getCallbackId());
            if (callback.getStatus() != CallbackStatus.FAILED) {
                throw new BizCheckFailException("只有投递失败的通知或回调可以重试，当前状态 " + callback.getStatus().getCode());
            }
            service.reopen(callback, support.callbackEvent(callback), support.now());
            support.succeeded(session, WorkflowMutation.CALLBACK, callback.getCallbackId(), "REOPENED");
            return new WorkflowMutation(WorkflowMutation.CALLBACK, callback.getCallbackId(),
                callback.getStatus().getCode(), callback.getVersion());
        });
    }

    /** 读取通知回调；不存在为 404。 */
    WorkflowCallback callback(long callbackId) {
        WorkflowCallback callback = callbacks.find(callbackId);
        if (callback == null) {
            throw support.notFound();
        }
        return callback;
    }
}

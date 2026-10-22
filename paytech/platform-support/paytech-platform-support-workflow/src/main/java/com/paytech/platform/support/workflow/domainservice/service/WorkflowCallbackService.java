package com.paytech.platform.support.workflow.domainservice.service;

import java.time.Instant;

import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowCallback;
import com.paytech.platform.support.workflow.domain.WorkflowCallbackDelivery;

/** 通知与回调的投递事实：读取、结果回写与人工重试；只写本库。 */
public interface WorkflowCallbackService {

    /**
     * 读取投递所需的事实。
     *
     * @param callbackId 通知回调 ID
     * @return 投递事实；不存在时为 null
     */
    WorkflowCallbackDelivery load(long callbackId);

    /**
     * 记录送达。
     *
     * @param callback 通知回调（PENDING）
     * @param now 送达时间
     */
    void markSent(WorkflowCallback callback, Instant now);

    /**
     * 记录一次失败；达到上限时置为 FAILED，否则保持 PENDING 等待重试。
     *
     * @param callback 通知回调（PENDING）
     * @param error 脱敏错误摘要
     * @param maxAttempts 尝试上限
     * @param now 失败时间
     * @return 是否已置为 FAILED
     */
    boolean markAttemptFailed(WorkflowCallback callback, String error, int maxAttempts, Instant now);

    /**
     * 人工重试：FAILED 回到 PENDING、清零尝试次数，并在同一事务登记新的补偿任务。
     *
     * @param callback 通知回调（FAILED）
     * @param event 补偿任务
     * @param now 重试时间
     */
    void reopen(WorkflowCallback callback, CompensationEventRequest event, Instant now);
}

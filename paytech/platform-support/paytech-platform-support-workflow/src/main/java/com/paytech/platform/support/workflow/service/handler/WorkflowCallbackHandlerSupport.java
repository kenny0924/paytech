package com.paytech.platform.support.workflow.service.handler;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.platform.support.workflow.core.enums.CallbackStatus;
import com.paytech.platform.support.workflow.domain.WorkflowCallbackDelivery;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowCallbackService;

/**
 * 两个通知回调 Handler 共用的身份解析与结果回写：每次尝试计入 attempt_count，达到上限置 FAILED 并结束任务（保留记录可人工重试）。
 */
final class WorkflowCallbackHandlerSupport {

    /** 无业务子类型的占位值。 */
    static final String NO_SUB_EVENT_TYPE = "-";

    private WorkflowCallbackHandlerSupport() {
    }

    /**
     * 解析事件键为通知回调 ID；不合法返回 null。
     *
     * @param event 补偿事件
     * @return 通知回调 ID 或 null
     */
    static Long callbackId(CompensationEvent event) {
        if (event.payloadJson() != null || !event.eventKey().matches("[1-9][0-9]{0,18}")) {
            return null;
        }
        try {
            return Long.parseLong(event.eventKey());
        } catch (NumberFormatException outOfRange) {
            return null;
        }
    }

    /**
     * 投递失败后的回写：未到上限可重试，到上限置 FAILED 后结束任务。
     *
     * @param service 通知回调服务
     * @param delivery 投递事实
     * @param code 稳定错误码
     * @param summary 脱敏摘要
     * @param maxAttempts 尝试上限
     * @param clock 时钟
     * @return 补偿结果
     */
    static CompensationHandlerResult failed(WorkflowCallbackService service, WorkflowCallbackDelivery delivery,
        String code, String summary, int maxAttempts, Clock clock) {
        boolean exhausted = service.markAttemptFailed(delivery.getCallback(), code + ": " + summary, maxAttempts,
            now(clock));
        return exhausted ? CompensationHandlerResult.success()
            : CompensationHandlerResult.retryableFailure(code, summary);
    }

    /**
     * 记录已不处于待投递（已送达、已失败或不存在）时，任务直接结束。
     *
     * @param delivery 投递事实
     * @return 无需再投递返回 true
     */
    static boolean finished(WorkflowCallbackDelivery delivery) {
        return delivery == null || delivery.getCallback().getStatus() != CallbackStatus.PENDING;
    }

    /**
     * UTC 微秒时间。
     *
     * @param clock 时钟
     * @return 时间
     */
    static Instant now(Clock clock) {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}

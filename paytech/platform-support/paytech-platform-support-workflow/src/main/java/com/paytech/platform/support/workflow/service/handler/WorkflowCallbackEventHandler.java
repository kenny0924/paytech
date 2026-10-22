package com.paytech.platform.support.workflow.service.handler;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublisher;
import com.paytech.platform.support.api.workflow.event.WorkflowCallbackRaisedPayload;
import com.paytech.platform.support.workflow.core.configuration.WorkflowProperties;
import com.paytech.platform.support.workflow.domain.WorkflowCallbackDelivery;
import com.paytech.platform.support.workflow.domain.runtime.CallbackTarget;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowCallbackService;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 工作流 CALLBACK 的事件发布（技术方案 4.2）：事务外从本库重建 WorkflowCallbackRaised v1，经 platform-support.events
 * 发布并等待 Broker 确认。
 * <li>eventId 固定为 {@code workflow-callback:{callbackId}}，重试发布同一 eventId，订阅方据此幂等。</li>
 * <li>事件只是通知；订阅方执行前须自行校验业务事实（D13）。不携带表单值或文件。</li>
 * <li>发布失败计入 attempt_count，达到上限置 FAILED 并结束任务；messaging 发布端未启用时同样按失败处理。</li>
 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowCallbackEventHandler implements CompensationEventHandler {

    /** 补偿事件类型。 */
    public static final String EVENT_TYPE = "WORKFLOW_CALLBACK_EVENT";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Clock clock;
    private final WorkflowProperties properties;
    private final WorkflowCallbackService service;
    private final ObjectProvider<DomainEventPublisher> publisher;

    /**
     * 装配依赖。
     *
     * @param clock 时钟
     * @param properties 工作流配置
     * @param service 通知回调服务
     * @param publisher 领域事件发布端；messaging 发布端未启用时不存在
     */
    public WorkflowCallbackEventHandler(Clock clock, WorkflowProperties properties, WorkflowCallbackService service,
        ObjectProvider<DomainEventPublisher> publisher) {
        this.clock = clock;
        this.properties = properties;
        this.service = service;
        this.publisher = publisher;
    }

    /**
     * 纯工厂：与通知回调记录同事务登记。
     *
     * @param callbackId 通知回调 ID
     * @return 登记请求
     */
    public static CompensationEventRequest buildEvent(long callbackId) {
        if (callbackId <= 0) {
            throw new IllegalArgumentException("通知回调 ID 必须为正数");
        }
        return new CompensationEventRequest(Long.toString(callbackId), EVENT_TYPE,
            WorkflowCallbackHandlerSupport.NO_SUB_EVENT_TYPE);
    }

    /** 持久化任务类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次 20 秒；任务次数上限大于业务上限，由业务记录决定何时置 FAILED。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(20), 30, Duration.ofSeconds(5), Duration.ofMinutes(10),
            2, 0.2);
    }

    /** 事务外重建并发布；已不待投递的记录直接结束。 */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("工作流回调发布不能处于已有事务中");
        }
        Long callbackId = WorkflowCallbackHandlerSupport.callbackId(event);
        if (callbackId == null) {
            return CompensationHandlerResult.terminalFailure("WORKFLOW_CALLBACK_INVALID", "工作流回调任务身份不合法");
        }
        WorkflowCallbackDelivery delivery = service.load(callbackId);
        if (WorkflowCallbackHandlerSupport.finished(delivery)) {
            return CompensationHandlerResult.success();
        }
        DomainEventPublisher available = publisher.getIfAvailable();
        try {
            if (available == null) {
                throw new ErrorException("messaging 发布端未启用");
            }
            available.publish(envelope(delivery));
        } catch (RuntimeException unpublished) {
            // 不把 Broker 返回信息写入摘要。
            return WorkflowCallbackHandlerSupport.failed(service, delivery, "WORKFLOW_CALLBACK_UNPUBLISHED",
                "回调事件发布失败：" + unpublished.getClass().getSimpleName(), properties.getCallbackMaxAttempts(), clock);
        }
        service.markSent(delivery.getCallback(), WorkflowCallbackHandlerSupport.now(clock));
        return CompensationHandlerResult.success();
    }

    /**
     * WorkflowCallbackRaised v1 信封；occurredAt 为记录登记时间。
     *
     * @param delivery 投递事实
     * @return 信封
     */
    DomainEvent envelope(WorkflowCallbackDelivery delivery) {
        CallbackTarget target = delivery.getTarget();
        return DomainEvent.builder().eventId("workflow-callback:" + delivery.getCallback().getCallbackId())
            .eventType(WorkflowCallbackRaisedPayload.EVENT_TYPE).schemaVersion(WorkflowCallbackRaisedPayload.SCHEMA_VERSION)
            .aggregate(WorkflowCallbackRaisedPayload.AGGREGATE_TYPE, Long.toString(delivery.getInstance().getInstanceId()))
            .aggregateVersion(target.instanceVersion()).occurredAt(delivery.getCallback().getCreateTime())
            .sourceService(properties.getEventSourceService()).instanceId(properties.getEventInstanceId())
            .payload(payload(delivery)).build();
    }

    /**
     * 事件 Payload；ID 与时间为字符串，空值不输出。
     *
     * @param delivery 投递事实
     * @return Payload 对象
     */
    ObjectNode payload(WorkflowCallbackDelivery delivery) {
        CallbackTarget target = delivery.getTarget();
        WorkflowCallbackRaisedPayload body = new WorkflowCallbackRaisedPayload(
            Long.toString(delivery.getCallback().getCallbackId()), target.callbackCode(),
            delivery.getCallback().getTriggerCode().getCode(), delivery.getDefinition().getDefinitionCode(),
            delivery.getVersion().getVersionNo(), Long.toString(delivery.getInstance().getInstanceId()),
            delivery.getInstance().getBusinessKey(),
            delivery.getNodeRun() == null ? null : delivery.getNodeRun().getNodeKey(),
            delivery.getNodeRun() == null ? null : delivery.getNodeRun().getRoundNo(), target.fromStatus(),
            target.toStatus(), target.actorId() == null ? null : Long.toString(target.actorId()), target.comment());
        ObjectNode node = JSON.valueToTree(body);
        node.properties().removeIf(entry -> entry.getValue().isNull());
        return node;
    }
}

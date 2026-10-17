package com.paytech.payment.execution.service.handler;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventPublisher;
import com.paytech.payment.execution.core.action.ActionType;
import com.paytech.payment.execution.core.configuration.PaymentEventProperties;
import com.paytech.payment.execution.core.dal.dataobject.ChannelTransactionRef;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.domain.PaymentSucceededPayload;
import com.paytech.payment.execution.domain.PaymentSucceededResult;
import com.paytech.payment.execution.domainservice.repository.ChannelTransactionRefRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentActionRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.integration.PaymentResultPublisher;

import lombok.AllArgsConstructor;

/**
 * 支付成功提交后发布 PaymentSucceeded
 * <li>eventKey 是支付尝试 ID；Handler 从尝试、意图、成功 PAY 动作及其渠道交易引用重建事件，补偿记录不保存业务内容。</li>
 * <li>双写（design/08 §3、D34）：先向旧 topic_payment_result 发布现有报文（Commerce 消费者尚未切换），再经 {@link DomainEventPublisher}
 * 向 payment.events 发布 Schema v1 信封（aggregateType=PAYMENT_ATTEMPT、aggregateId=尝试 ID、aggregateVersion=尝试版本）。</li>
 * <li>两者都收到 Broker 确认才成功；任一失败按可重试处理，重试会重发两者，消费方按事件键 / eventId 与 Payload Hash 防重。</li>
 */
@Component
@AllArgsConstructor
public class PaymentSucceededHandler implements CompensationEventHandler {
    /** 事件类型。 */
    public static final String EVENT_TYPE = "PAYMENT_SUCCEEDED";

    private final PaymentAttemptRepository attempts;
    private final PaymentIntentRepository intents;
    private final PaymentActionRepository actions;
    private final ChannelTransactionRefRepository refs;
    private final PaymentResultPublisher publisher;
    private final DomainEventPublisher domainEvents;
    private final PaymentEventProperties eventProperties;

    /** 纯工厂：只能与收款成功在同一本地事务登记。 */
    public static CompensationEventRequest buildEvent(long attemptId) {
        if (attemptId <= 0) {
            throw new IllegalArgumentException("Invalid payment attempt identity");
        }
        return new CompensationEventRequest(Long.toString(attemptId), EVENT_TYPE, "-");
    }

    /** 事件类型。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 每次尝试 20 秒，最多 12 次，5 秒起指数退避至 30 分钟。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(20), 12, Duration.ofSeconds(5), Duration.ofMinutes(30), 2, 0.2);
    }

    /**
     * 发布支付成功
     * <li>尝试不存在或未成功、意图缺失或未指向该尝试、最后动作不是成功的 PAY、渠道交易引用不唯一，为终态失败，保留记录供人工处理。</li>
     * <li>Broker 未确认或连接失败可重试。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompensationHandlerResult.terminalFailure("PAYMENT_SUCCEEDED_TRANSACTION_ACTIVE", "Payment result must publish outside a transaction");
        }
        if (event.payloadJson() != null || !event.eventKey().matches("[1-9][0-9]{0,18}")) {
            return CompensationHandlerResult.terminalFailure("PAYMENT_SUCCEEDED_INVALID", "Payment result event is malformed");
        }
        PaymentSucceededResult result = rebuild(Long.parseLong(event.eventKey()));
        if (result == null) {
            return CompensationHandlerResult.terminalFailure("PAYMENT_SUCCEEDED_INVALID", "Payment attempt is not a completed sale");
        }
        try {
            publisher.publishSucceeded(result);
            domainEvents.publish(envelope(result, eventProperties));
            return CompensationHandlerResult.success();
        } catch (RuntimeException unavailable) {
            // 不把 Broker 返回信息写入任务错误摘要。
            return CompensationHandlerResult.retryableFailure("PAYMENT_SUCCEEDED_UNPUBLISHED", "Payment result publication requires retry");
        }
    }

    /** 从权威表重建成功事实；任一关联不成立时返回空。 */
    PaymentSucceededResult rebuild(long attemptId) {
        PaymentAttempt attempt = attempts.selectById(attemptId);
        if (attempt == null || attempt.getAttemptStatus() != AttemptStatus.SUCCEEDED) {
            return null;
        }
        PaymentIntent intent = intents.findIntent(attempt.getPaymentIntentId());
        if (intent == null || !Objects.equals(intent.getSuccessfulAttemptId(), attemptId)) {
            return null;
        }
        PaymentAction pay = actions.findLatestByAttempt(attemptId);
        if (pay == null || pay.getActionType() != ActionType.PAY || pay.getActionStatus() != ActionStatus.SUCCEEDED) {
            return null;
        }
        List<ChannelTransactionRef> payRefs = refs.findByAction(pay.getPaymentActionId());
        if (payRefs.size() != 1) {
            return null;
        }
        return result(intent, attempt, pay, payRefs.getFirst());
    }

    /** PaymentSucceeded Schema v1 信封；eventId 默认取 AggregateKey 四段式事件键，补偿重试不变。 */
    static DomainEvent envelope(PaymentSucceededResult result, PaymentEventProperties properties) {
        return DomainEvent.builder().eventType(PaymentSucceededPayload.EVENT_TYPE)
            .schemaVersion(PaymentSucceededPayload.SCHEMA_VERSION)
            .aggregate(PaymentSucceededPayload.AGGREGATE_TYPE, Long.toString(result.getPaymentAttemptId()))
            .aggregateVersion(result.getAttemptVersion()).occurredAt(result.getSucceededAt())
            .sourceService(properties.getSourceService()).instanceId(properties.getInstanceId())
            .payload(PaymentSucceededPayload.json(result)).build();
    }

    /** 组装事件事实；成功时间优先取渠道结果时间。 */
    static PaymentSucceededResult result(PaymentIntent intent, PaymentAttempt attempt, PaymentAction pay,
        ChannelTransactionRef ref) {
        return PaymentSucceededResult.builder().paymentAttemptId(attempt.getPaymentAttemptId())
            .attemptVersion(attempt.getVersion()).clientId(intent.getClientId()).sourceType(intent.getSourceType())
            .sourceId(intent.getSourceId()).paymentIntentId(intent.getPaymentIntentId())
            .sourceSubmissionId(attempt.getSourceSubmissionId()).entityNo(intent.getEntityNo()).amount(intent.getAmount())
            .currency(intent.getCurrency()).capturedAmount(attempt.getCapturedAmount())
            .succeededAt(Objects.requireNonNullElse(pay.getChannelResultTime(), attempt.getUpdateTime()))
            .paymentMethodCode(attempt.getPaymentMethodCode()).channelId(attempt.getChannelId())
            .channelAccountId(attempt.getChannelAccountId()).midId(attempt.getMidId())
            .channelTransactionRef(ref.getExternalTransactionId()).build();
    }
}

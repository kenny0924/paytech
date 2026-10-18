package com.paytech.platform.support.delivery.service.impl;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookAttempt;
import com.paytech.platform.support.delivery.core.enums.WebhookAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookFailureCode;
import com.paytech.platform.support.delivery.domain.WebhookAttemptOutcome;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryContext;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookAttemptRepository;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookDeliveryRepository;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookEndpointRepository;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookEventRepository;
import com.paytech.platform.support.delivery.domainservice.repository.WebhookSigningSecretRepository;
import com.paytech.platform.support.delivery.service.WebhookDeliveryService;

import lombok.RequiredArgsConstructor;

/** 全部本地写入使用显式短事务；投递版本 CAS 同时保护投递与尝试。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "webhook.enabled", havingValue = "true")
public class WebhookDeliveryServiceImpl implements WebhookDeliveryService {

    private final PlatformTransactionManager transactionManager;
    private final WebhookEventRepository webhookEvents;
    private final WebhookAttemptRepository attempts;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookEndpointRepository endpoints;
    private final WebhookSigningSecretRepository secrets;

    /** 同一快照读取投递及其关联事实；关联缺失说明数据被破坏。 */
    @Override
    public WebhookDeliveryContext load(long deliveryId) {
        return transaction(true).execute(status -> {
            var delivery = deliveries.find(deliveryId);
            if (delivery == null) {
                throw new ErrorException("Webhook 投递事实缺失");
            }
            var event = webhookEvents.find(delivery.getWebhookEventId());
            var endpoint = endpoints.find(delivery.getEndpointId());
            if (event == null || endpoint == null) {
                throw new ErrorException("Webhook 投递的事件或端点缺失");
            }
            var processing = delivery.getStatus() == WebhookDeliveryStatus.PROCESSING
                ? attempts.findProcessing(deliveryId) : null;
            return new WebhookDeliveryContext(delivery, event, endpoint, secrets.findEndpoint(endpoint.getEndpointId()),
                processing);
        });
    }

    /** 未完成的投递记 CANCELLED；遗留 PROCESSING 尝试一并记为中断。 */
    @Override
    public void cancel(WebhookDeliveryContext context, Instant now) {
        transaction(false).executeWithoutResult(status -> {
            finishInterrupted(context, now);
            var delivery = context.getDelivery();
            deliveries.transition(delivery, WebhookDeliveryStatus.CANCELLED, delivery.getAttemptCount(),
                delivery.getLastAttemptTime(), now);
        });
    }

    /** 中断的发送按失败计入次数；次数用尽记 DEAD。 */
    @Override
    public void interrupt(WebhookDeliveryContext context, int maximumAttempts, Instant now) {
        transaction(false).executeWithoutResult(status -> {
            finishInterrupted(context, now);
            var delivery = context.getDelivery();
            var next = delivery.getAttemptCount() >= maximumAttempts ? WebhookDeliveryStatus.DEAD
                : WebhookDeliveryStatus.RETRY_WAIT;
            deliveries.transition(delivery, next, delivery.getAttemptCount(), delivery.getLastAttemptTime(), now);
        });
    }

    /** 先取得投递所有权再插入尝试，任一步失败都回滚。 */
    @Override
    public void begin(WebhookDeliveryContext context, WebhookAttempt attempt, Instant now) {
        transaction(false).executeWithoutResult(status -> {
            var delivery = context.getDelivery();
            deliveries.transition(delivery, WebhookDeliveryStatus.PROCESSING, delivery.getAttemptCount() + 1, now,
                now);
            attempts.insert(attempt);
        });
    }

    /** 尝试与投递同一事务写入结果；投递版本保证晚到的旧执行者无法覆盖。 */
    @Override
    public void complete(WebhookDeliveryContext context, WebhookAttempt attempt, WebhookAttemptOutcome outcome,
        int maximumAttempts, Instant now) {
        transaction(false).executeWithoutResult(status -> {
            var delivery = context.getDelivery();
            attempts.finish(attempt, outcome.isSucceeded() ? WebhookAttemptStatus.SUCCEEDED
                : WebhookAttemptStatus.FAILED, outcome.getHttpStatus(), outcome.getFailureCode(),
                outcome.getDurationMs(), now);
            deliveries.transition(delivery, nextStatus(outcome, delivery.getAttemptCount(), maximumAttempts),
                delivery.getAttemptCount(), delivery.getLastAttemptTime(), now);
        });
    }

    /** 成功记 SUCCEEDED；失败时次数用尽记 DEAD，否则 RETRY_WAIT。 */
    static WebhookDeliveryStatus nextStatus(WebhookAttemptOutcome outcome, int attemptCount, int maximumAttempts) {
        if (outcome.isSucceeded()) {
            return WebhookDeliveryStatus.SUCCEEDED;
        }
        return attemptCount >= maximumAttempts ? WebhookDeliveryStatus.DEAD : WebhookDeliveryStatus.RETRY_WAIT;
    }

    /** 结束遗留的 PROCESSING 尝试；不得在没有遗留尝试时处理 PROCESSING 投递。 */
    void finishInterrupted(WebhookDeliveryContext context, Instant now) {
        if (context.getDelivery().getStatus() != WebhookDeliveryStatus.PROCESSING) {
            return;
        }
        if (context.getProcessingAttempt() == null) {
            throw new ErrorException("Webhook 投递处于发送中但缺少当前尝试");
        }
        attempts.finish(context.getProcessingAttempt(), WebhookAttemptStatus.FAILED, null,
            WebhookFailureCode.INTERRUPTED, null, now);
    }

    /** 读写模板独立创建，不改变宿主公共只读事务 Bean 的属性。 */
    TransactionTemplate transaction(boolean readOnly) {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(readOnly);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(15);
        return transaction;
    }
}

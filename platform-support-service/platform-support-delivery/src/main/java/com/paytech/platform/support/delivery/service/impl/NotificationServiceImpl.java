package com.paytech.platform.support.delivery.service.impl;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.delivery.core.configuration.NotificationProperties;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationAttempt;
import com.paytech.platform.support.delivery.core.enums.NotificationAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationDeliveryStatus;
import com.paytech.platform.support.delivery.core.enums.NotificationResolutionSource;
import com.paytech.platform.support.delivery.domain.NotificationExecutionContext;
import com.paytech.platform.support.delivery.domainservice.repository.*;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;
import com.paytech.platform.support.delivery.service.NotificationService;

import lombok.RequiredArgsConstructor;

/** 全部本地写入使用显式短事务，领域版本同时保护 Delivery 与 Attempt。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class NotificationServiceImpl implements NotificationService {

    private final NotificationProperties properties;
    private final PlatformTransactionManager transactionManager;
    private final NotificationRouteRepository routes;
    private final NotificationRequestRepository requests;
    private final NotificationAttemptRepository attempts;
    private final NotificationDeliveryRepository deliveries;
    private final NotificationProviderRepository providers;
    private final NotificationTemplateRepository templates;
    private final NotificationTemplateContentRepository contents;

    /** 读取操作只用本库；恢复发送与查询采用各自需要的配置。 */
    @Override
    public NotificationExecutionContext load(long deliveryId) {
        return transaction(true).execute(tx -> {
            var delivery = deliveries.find(deliveryId);
            if (delivery == null)
                throw new ErrorException("通知投递事实缺失");
            var request = requests.find(delivery.getRequestId());
            if (request == null || delivery.getVersion() <= 0 || delivery.getAttemptCount() < 0) {
                throw new ErrorException("通知请求与投递事实不一致");
            }
            if (delivery.getStatus() == NotificationDeliveryStatus.DELIVERED
                || delivery.getStatus() == NotificationDeliveryStatus.FAILED) {
                return new NotificationExecutionContext(request, delivery, null, null, null, null, null);
            }
            if (delivery.getStatus() == NotificationDeliveryStatus.PENDING
                || delivery.getStatus() == NotificationDeliveryStatus.RETRY_WAIT) {
                var template = templates.find(request.getNotificationTemplateId());
                var route = routes.find(request.getNotificationRouteId());
                Long providerId =
                    route == null ? null : delivery.getAttemptCount() > 0 && route.getFallbackProviderId() != null
                        ? route.getFallbackProviderId() : route.getPrimaryProviderId();
                var provider = providerId == null ? null : providers.find(providerId);
                var content = contents.findLocale(request.getNotificationTemplateId(),
                    delivery.getLocale() == null ? request.getDefaultLocale() : delivery.getLocale());
                return new NotificationExecutionContext(request, delivery, null, template, content, route, provider);
            }
            var attempt = attempts.findCurrent(deliveryId);
            if (attempt == null || attempt.getStatus() == NotificationAttemptStatus.FAILED) {
                throw new ErrorException("通知当前发送尝试缺失或与投递状态不一致");
            }
            var provider = providers.find(attempt.getProviderId());
            if (provider == null)
                throw new ErrorException("通知原发送账号缺失");
            return new NotificationExecutionContext(request, delivery, attempt, null, null, null, provider);
        });
    }

    /** 先获得本地状态所有权再插入尝试，任一步失败都回滚。 */
    @Override
    public void beginSend(NotificationExecutionContext context, NotificationAttempt attempt, Instant now) {
        transaction(false).executeWithoutResult(tx -> {
            if (!context.canSend()) {
                throw new ErrorException("当前通知状态不可新建发送");
            }
            deliveries.transition(context.getDelivery(), NotificationDeliveryStatus.PROCESSING,
                context.getDelivery().getAttemptCount() + 1, null, now);
            attempts.insert(attempt);
        });
    }

    /** 接管推进版本；查询结果保存必须继续持有该版本。 */
    @Override
    public void beginQuery(NotificationExecutionContext context, Instant now) {
        transaction(false).executeWithoutResult(tx -> {
            var delivery = context.getDelivery();
            var state = delivery.getStatus() == NotificationDeliveryStatus.PROCESSING
                ? NotificationDeliveryStatus.UNKNOWN : delivery.getStatus();
            deliveries.transition(delivery, state, delivery.getAttemptCount(), null, now);
            if (context.getAttempt().getStatus() == NotificationAttemptStatus.PROCESSING) {
                attempts.resolve(context.getAttempt(), NotificationAttemptStatus.UNKNOWN, null, "SEND_INTERRUPTED",
                    NotificationResolutionSource.PROVIDER_QUERY, now);
            }
        });
    }

    /** 不确定结果保留业务未知；已受理的投递不会再次发送。 */
    @Override
    public void complete(NotificationExecutionContext context, NotificationAttempt attempt, EmailProviderResult result,
        boolean query, Instant now) {
        transaction(false).executeWithoutResult(tx -> {
            var delivery = context.getDelivery();
            var outcome = result.getOutcome();
            boolean accepted = attempt.getStatus() == NotificationAttemptStatus.ACCEPTED;
            NotificationDeliveryStatus deliveryStatus;
            NotificationAttemptStatus attemptStatus;
            Instant nextRetry = null;
            switch (outcome) {
                case ACCEPTED, DELIVERED, UNDELIVERABLE -> {
                    deliveryStatus = outcome == EmailProviderResult.Outcome.DELIVERED
                        ? NotificationDeliveryStatus.DELIVERED : outcome == EmailProviderResult.Outcome.UNDELIVERABLE
                            ? NotificationDeliveryStatus.FAILED : NotificationDeliveryStatus.SENT;
                    attemptStatus = NotificationAttemptStatus.ACCEPTED;
                }
                case REJECTED, RETRYABLE_REJECTED -> {
                    if (accepted)
                        throw new ErrorException("供应商查询与已受理事实冲突");
                    boolean retry = outcome == EmailProviderResult.Outcome.RETRYABLE_REJECTED
                        && delivery.getAttemptCount() < properties.getMaximumSendAttempts();
                    deliveryStatus = retry ? NotificationDeliveryStatus.RETRY_WAIT : NotificationDeliveryStatus.FAILED;
                    attemptStatus = NotificationAttemptStatus.FAILED;
                    if (retry)
                        nextRetry = now.plus(properties.getRetryDelay());
                }
                default -> {
                    deliveryStatus = accepted ? NotificationDeliveryStatus.SENT : NotificationDeliveryStatus.UNKNOWN;
                    attemptStatus = accepted ? NotificationAttemptStatus.ACCEPTED : NotificationAttemptStatus.UNKNOWN;
                }
            }
            deliveries.transition(delivery, deliveryStatus, delivery.getAttemptCount(), nextRetry, now);
            attempts.resolve(attempt, attemptStatus, result.getMessageId(), result.getCode(),
                query ? NotificationResolutionSource.PROVIDER_QUERY : NotificationResolutionSource.SEND_RESPONSE, now);
        });
    }

    /** 仅用于没有外发的确定本地失败，不把 UNKNOWN 改为失败。 */
    @Override
    public void failBeforeSend(NotificationExecutionContext context, Instant now) {
        if (!context.canSend())
            throw new ErrorException("不能将未决外发按本地失败结束");
        transaction(false).executeWithoutResult(tx -> deliveries.transition(context.getDelivery(),
            NotificationDeliveryStatus.FAILED, context.getDelivery().getAttemptCount(), null, now));
    }

    /**
     * 不支持查询的 Provider 在安全窗口后放弃确认原发送。
     * <li>遗留 PROCESSING 尝试按发送中断记为 UNKNOWN，不写确认时间</li>
     * <li>resend 时 Delivery 版本推进到 RETRY_WAIT 且立即可发，旧执行者的晚回写被版本拒绝</li>
     */
    @Override
    public void releaseUnknown(NotificationExecutionContext context, boolean resend, Instant now) {
        var delivery = context.getDelivery();
        var attempt = context.getAttempt();
        boolean interrupted = attempt.getStatus() == NotificationAttemptStatus.PROCESSING;
        if (!resend && !interrupted) {
            return;
        }
        transaction(false).executeWithoutResult(tx -> {
            var state = resend ? NotificationDeliveryStatus.RETRY_WAIT : NotificationDeliveryStatus.UNKNOWN;
            deliveries.transition(delivery, state, delivery.getAttemptCount(), resend ? now : null, now);
            if (interrupted) {
                attempts.resolve(attempt, NotificationAttemptStatus.UNKNOWN, null, "SEND_INTERRUPTED",
                    NotificationResolutionSource.SEND_RESPONSE, now);
            }
        });
    }

    /** 读写模板独立创建，不改变宿主公共只读事务 Bean 的属性。 */
    TransactionTemplate transaction(boolean readOnly) {
        var tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(readOnly);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setTimeout(15);
        return tx;
    }
}

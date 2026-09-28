package com.paytech.platform.support.delivery.ext.eventhandler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.*;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationAttempt;
import com.paytech.platform.support.delivery.core.enums.NotificationAttemptStatus;
import com.paytech.platform.support.delivery.domain.NotificationExecutionContext;
import com.paytech.platform.support.delivery.domain.NotificationInput;
import com.paytech.platform.support.delivery.domain.NotificationMessage;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderRequest;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;
import com.paytech.platform.support.delivery.integration.provider.EmailProviders;
import com.paytech.platform.support.delivery.integration.vault.NotificationAddressVault;
import com.paytech.platform.support.delivery.service.NotificationService;

import lombok.RequiredArgsConstructor;

/** 每次执行只推进一个收件人的发送或原发送查询，依赖公共 Claim/Lease 和领域 CAS。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class NotificationDeliveryHandler implements CompensationEventHandler {

    private static final String EVENT_TYPE = "NOTIFICATION_DELIVERY";
    private final Clock clock;
    private final IdGenerator ids;
    private final EmailProviders providers;
    private final NotificationService service;
    private final NotificationAddressVault vault;

    /** 最小事件引用与业务事实在同一事务登记，不复制地址、参数或 Vault Token。 */
    public static CompensationEventRequest buildEvent(long deliveryId) {
        if (deliveryId <= 0)
            throw new IllegalArgumentException("投递标识必须为正数");
        return new CompensationEventRequest(Long.toString(deliveryId), EVENT_TYPE, "-");
    }

    /** 持久化任务类型保持稳定。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 查询与恢复共享有界预算；耗尽只使任务失败，不改写 UNKNOWN 或 SENT。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(20), 24, Duration.ofSeconds(30),
            Duration.ofMinutes(15), 2, 0.2);
    }

    /** 事务外入口；重入只恢复当前事实，不把任务次数当作发送次数。 */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("通知执行入口不能处于已有事务中");
        }
        if (event.payloadJson() != null || !event.eventKey().matches("[1-9][0-9]{0,18}")) {
            return CompensationHandlerResult.terminalFailure("NOTIFICATION_EVENT_INVALID", "通知任务身份不合法");
        }
        long deliveryId;
        try {
            deliveryId = Long.parseLong(event.eventKey());
        } catch (NumberFormatException invalid) {
            return CompensationHandlerResult.terminalFailure("NOTIFICATION_EVENT_INVALID", "通知任务身份超出范围");
        }
        var context = service.load(deliveryId);
        if (context.isTerminal())
            return CompensationHandlerResult.success();
        try {
            if (context.canSend()) {
                send(context);
            } else {
                query(context);
            }
        } catch (ConcurrentRequestException ownershipChanged) {
            // 本地版本已交接不代表投递失败；保留任务，让下一轮读取新事实。
            return CompensationHandlerResult.retryableFailure("NOTIFICATION_CONCURRENT", "通知状态已由其他执行者推进");
        }
        return context.isTerminal() ? CompensationHandlerResult.success()
            : CompensationHandlerResult.retryableFailure("NOTIFICATION_PENDING", "通知仍待发送或确认原发送结果");
    }

    /** 渲染和 Reveal 成功后才登记一次发送；调用失败仍保留原 Attempt 供查询。 */
    void send(NotificationExecutionContext context) {
        var delivery = context.getDelivery();
        if (delivery.getNextRetryTime() != null && delivery.getNextRetryTime().isAfter(now()))
            return;
        NotificationMessage message;
        try {
            message = context.render();
        } catch (BizCheckFailException invalidConfiguration) {
            service.failBeforeSend(context, now());
            return;
        }
        var provider = providers.require(context.getProvider().getProviderCode());
        String email = NotificationInput.canonicalEmail(vault.reveal(delivery.getAddressVaultToken()));
        var attempt = new NotificationAttempt();
        attempt.setAttemptId(ids.nextId());
        attempt.setDeliveryId(delivery.getDeliveryId());
        attempt.setProviderId(context.getProvider().getProviderId());
        attempt.setProviderRequestNo(provider.requestNo(attempt.getAttemptId()));
        validateRequestNo(attempt.getProviderRequestNo());
        attempt.setStatus(NotificationAttemptStatus.PROCESSING);
        attempt.setStartedTime(now());
        attempt.setCreateTime(attempt.getStartedTime());
        attempt.setUpdateTime(attempt.getStartedTime());
        if (Thread.currentThread().isInterrupted())
            throw new ErrorException("通知发送线程已中断");
        service.beginSend(context, attempt, attempt.getStartedTime());
        EmailProviderResult result;
        try {
            if (Thread.currentThread().isInterrupted()) {
                throw new ErrorException("通知发送线程已中断");
            }
            result = validResult(provider.send(new EmailProviderRequest(attempt.getProviderRequestNo(), email,
                message.getSubject(), message.getBody())));
        } catch (RuntimeException uncertain) {
            // 外发后的异常只表示未知；不在这里重发，也不保存可能包含地址的异常原文。
            result = unknown();
        }
        service.complete(context, attempt, result, false, now());
    }

    /** 查询始终使用历史账号与请求号；不读取当前模板，不 Reveal，不创建新 Attempt。 */
    void query(NotificationExecutionContext context) {
        var provider = providers.require(context.getProvider().getProviderCode());
        var attempt = context.getAttempt();
        service.beginQuery(context, now());
        EmailProviderResult result;
        try {
            result = validResult(provider.query(attempt.getProviderRequestNo(), attempt.getProviderMessageId()));
        } catch (RuntimeException uncertain) {
            result = unknown();
        }
        service.complete(context, attempt, result, true, now());
    }

    /** 只接受有界非敏感结果码，不持久化供应商任意错误文字。 */
    EmailProviderResult validResult(EmailProviderResult result) {
        if (result == null || result.getOutcome() == null || result.getCode() == null
            || !result.getCode().matches("[A-Z][A-Z0-9_]{0,63}")
            || result.getMessageId() != null && (result.getMessageId().isBlank() || result.getMessageId().length() > 255
                || result.getMessageId().codePoints().anyMatch(Character::isISOControl))) {
            return unknown();
        }
        return result;
    }

    /** 供应商请求号外发前固定；禁止空白、控制字符或超长身份。 */
    void validateRequestNo(String requestNo) {
        if (requestNo == null || requestNo.isBlank() || requestNo.length() > 128 || !requestNo.equals(requestNo.strip())
            || requestNo.codePoints().anyMatch(Character::isISOControl)) {
            throw new ErrorException("邮件供应商请求号不合法");
        }
    }

    /** 未知结果不得被解释为确定失败或允许切备用账号。 */
    EmailProviderResult unknown() {
        return new EmailProviderResult(EmailProviderResult.Outcome.UNKNOWN, null, "RESULT_UNKNOWN");
    }

    /** 与 MySQL DATETIME(6) 保持 UTC 微秒精度。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}

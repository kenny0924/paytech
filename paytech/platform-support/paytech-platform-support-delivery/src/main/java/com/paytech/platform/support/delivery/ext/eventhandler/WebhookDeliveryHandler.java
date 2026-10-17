package com.paytech.platform.support.delivery.ext.eventhandler;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.delivery.core.configuration.WebhookProperties;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookAttempt;
import com.paytech.platform.support.delivery.core.dal.dataobject.WebhookSigningSecret;
import com.paytech.platform.support.delivery.core.enums.WebhookAttemptStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryContext;
import com.paytech.platform.support.delivery.domain.WebhookEnvelope;
import com.paytech.platform.support.delivery.domain.WebhookSignature;
import com.paytech.platform.support.delivery.integration.webhook.WebhookHttpSender;
import com.paytech.platform.support.delivery.integration.webhook.WebhookSecretVault;
import com.paytech.platform.support.delivery.service.WebhookDeliveryService;

import lombok.RequiredArgsConstructor;

/**
 * 每次执行最多做一次 HTTP 尝试；重试节奏由补偿策略的等比退避承担
 * <li>间隔约 1m、3m、9m、27m、81m、4h、12h、24h、24h；投递尝试上限见 webhook.maximum-attempts，用完记 DEAD</li>
 * <li>超时、断连一律按失败重试：至少一次投递，Client 按 eventId 去重；不做结果查询</li>
 * <li>Vault 不可用或并发冲突不消耗尝试次数；补偿次数上限 20 为此留出余量</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "webhook.enabled", havingValue = "true")
public class WebhookDeliveryHandler implements CompensationEventHandler {

    /** 签名密钥暂不可读取：本次不外发、不计尝试，等待重试。 */
    public static final String SECRET_UNAVAILABLE = "WEBHOOK_SECRET_UNAVAILABLE";

    /** 冻结事件与摘要不一致：任务终止，待人工处理。 */
    public static final String PAYLOAD_INTEGRITY = "WEBHOOK_PAYLOAD_INTEGRITY";

    private static final String EVENT_TYPE = "WEBHOOK_DELIVERY";
    private final Clock clock;
    private final IdGenerator ids;
    private final WebhookHttpSender sender;
    private final WebhookSecretVault vault;
    private final WebhookProperties properties;
    private final WebhookDeliveryService service;

    /** 最小事件引用与投递在同一事务登记，不复制 Envelope、地址或密钥。 */
    public static CompensationEventRequest buildEvent(long deliveryId) {
        if (deliveryId <= 0) {
            throw new IllegalArgumentException("投递标识必须为正数");
        }
        return new CompensationEventRequest(Long.toString(deliveryId), EVENT_TYPE, "-");
    }

    /** 持久化任务类型保持稳定。 */
    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /** 首次重试 1 分钟，每次乘 3，封顶 24 小时；Handler 最长 30 秒。 */
    @Override
    public CompensationExecutionPolicy policy() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(30), 20, Duration.ofMinutes(1),
            Duration.ofHours(24), 3.0, 0.2);
    }

    /** 事务外入口；终态返回成功，DEAD 是正常业务终态，不再由补偿任务报警。 */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("Webhook 投递入口不能处于已有事务中");
        }
        Long deliveryId = deliveryId(event);
        if (deliveryId == null) {
            return CompensationHandlerResult.terminalFailure("WEBHOOK_EVENT_INVALID", "Webhook 任务身份不合法");
        }
        var context = service.load(deliveryId);
        if (context.getDelivery().getStatus().isTerminal()) {
            return CompensationHandlerResult.success();
        }
        try {
            return advance(context);
        } catch (ConcurrentRequestException ownershipChanged) {
            // 投递版本已被其他执行者推进；保留任务，下一轮读取新事实。
            return CompensationHandlerResult.retryableFailure("WEBHOOK_CONCURRENT", "Webhook 投递已由其他执行者推进");
        }
    }

    /**
     * 推进一步：取消、收敛中断，或发送一次
     *
     * @param context 本轮读取的投递事实
     * @return 终态返回成功，RETRY_WAIT 返回可重试
     */
    CompensationHandlerResult advance(WebhookDeliveryContext context) {
        Instant now = now();
        if (!context.deliverable()) {
            service.cancel(context, now);
            return CompensationHandlerResult.success();
        }
        if (context.getDelivery().getStatus() == WebhookDeliveryStatus.PROCESSING) {
            service.interrupt(context, properties.getMaximumAttempts(), now);
            if (context.getDelivery().getStatus().isTerminal()) {
                return CompensationHandlerResult.success();
            }
        }
        byte[] body = WebhookEnvelope.canonicalize(context.getEvent().getPayload());
        if (!WebhookEnvelope.sha256Hex(body).equals(context.getEvent().getPayloadHash())) {
            return CompensationHandlerResult.terminalFailure(PAYLOAD_INTEGRITY,
                "Webhook 冻结事件与摘要不一致，需人工处理");
        }
        return send(context, body, now);
    }

    /** 读取密钥成功后才登记一次尝试；发送器不抛网络异常。 */
    CompensationHandlerResult send(WebhookDeliveryContext context, byte[] body, Instant now) {
        var active = context.activeSecret();
        var grace = context.graceSecret(now);
        List<String> keys;
        try {
            keys = reveal(active, grace);
        } catch (RuntimeException unavailable) {
            // Vault 暂不可用或值不合法：本次不外发、不计尝试，原因不含密钥或 Token。
            return CompensationHandlerResult.retryableFailure(SECRET_UNAVAILABLE, "Webhook 签名密钥暂不可读取");
        }
        var attempt = newAttempt(context, active, grace, now);
        service.begin(context, attempt, now);
        String signature = WebhookSignature.header(now.getEpochSecond(), body, keys);
        var outcome = sender.send(URI.create(attempt.getRequestUrl()), body, signature);
        service.complete(context, attempt, outcome, properties.getMaximumAttempts(), now());
        return context.getDelivery().getStatus().isTerminal() ? CompensationHandlerResult.success()
            : CompensationHandlerResult.retryableFailure("WEBHOOK_RETRY_WAIT", "Webhook 投递等待下一次尝试");
    }

    /** ACTIVE 在前、宽限密钥在后。 */
    List<String> reveal(WebhookSigningSecret active, WebhookSigningSecret grace) {
        var keys = new ArrayList<String>();
        keys.add(vault.reveal(active.getSecretVaultToken()));
        if (grace != null) {
            keys.add(vault.reveal(grace.getSecretVaultToken()));
        }
        return keys;
    }

    /** 本次 PROCESSING 尝试：固定请求地址与签名密钥。 */
    WebhookAttempt newAttempt(WebhookDeliveryContext context, WebhookSigningSecret active, WebhookSigningSecret grace,
        Instant now) {
        var attempt = new WebhookAttempt();
        attempt.setAttemptId(ids.nextId());
        attempt.setDeliveryId(context.getDelivery().getDeliveryId());
        attempt.setAttemptNo(context.getDelivery().getAttemptCount() + 1);
        attempt.setRequestUrl(context.getEndpoint().getUrl());
        attempt.setSigningSecretId(active.getSigningSecretId());
        attempt.setGraceSecretId(grace == null ? null : grace.getSigningSecretId());
        attempt.setStatus(WebhookAttemptStatus.PROCESSING);
        attempt.setStartedTime(now);
        attempt.setCreateTime(now);
        attempt.setUpdateTime(now);
        return attempt;
    }

    /** 事件只携带正数投递标识且没有 Payload；不合法或超出 long 范围时返回空。 */
    Long deliveryId(CompensationEvent event) {
        if (event.payloadJson() != null || !event.eventKey().matches("[1-9][0-9]{0,18}")) {
            return null;
        }
        try {
            return Long.parseLong(event.eventKey());
        } catch (NumberFormatException outOfRange) {
            return null;
        }
    }

    /** 与 MySQL DATETIME(6) 保持 UTC 微秒精度。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}

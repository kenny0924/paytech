package com.paytech.platform.support.service.processor.webhook;

import java.security.SecureRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.delivery.core.configuration.WebhookProperties;
import com.paytech.platform.support.delivery.domain.RotateWebhookSecretCommand;
import com.paytech.platform.support.delivery.domain.RotateWebhookSecretResult;
import com.paytech.platform.support.delivery.domain.WebhookSignature;
import com.paytech.platform.support.delivery.integration.webhook.WebhookSecretVault;
import com.paytech.platform.support.delivery.service.WebhookManagementService;
import com.paytech.platform.support.service.processor.webhook.request.RotateWebhookSecretRequest;
import com.paytech.platform.support.service.processor.webhook.response.RotateWebhookSecretResponse;

import lombok.AllArgsConstructor;

/**
 * 轮换端点签名密钥
 * <li>默认旧密钥继续参与签名 webhook.secret-grace-period（1 小时），立即作废时旧密钥马上失效</li>
 * <li>先确认管理权限与端点存在，再在事务外保存新密钥到 Vault；写事务内重检权限、轮换并审计</li>
 * <li>新密钥明文只在响应中返回一次</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"operations.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class RotateWebhookSecretProcessor
    extends AbstractLockProcessor<RotateWebhookSecretRequest, RotateWebhookSecretResponse> {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookSecretVault vault;
    private final WebhookProperties properties;
    private final WebhookManagementSupport support;
    private final WebhookManagementService management;

    @Override
    protected LockCommand lockCommand(RotateWebhookSecretRequest request) {
        return LockCommand.tryLock(WebhookManagementSupport.endpointLockKey(request.getEndpointId()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected RotateWebhookSecretResponse processWithinLock(RotateWebhookSecretRequest request) {
        // 事务外准备
        support.requireManage(request.getActor(), request.getSession());
        management.endpointTenant(request.getEndpointId());
        String secret = WebhookSignature.generateSecret(RANDOM);
        String token = vault.protect(secret);

        // 轮换与审计
        RotateWebhookSecretResult result = support.write(request.getActor(), request.getSession(),
            request.getReason(), (context, now) -> management.rotate(new RotateWebhookSecretCommand(
                request.getEndpointId(), token, request.isImmediate() ? now : now.plus(properties.getSecretGracePeriod()),
                now)), RotateWebhookSecretResult::getChange);
        return new RotateWebhookSecretResponse(result.getRotationNo(), secret, result.getPreviousValidUntil());
    }
}

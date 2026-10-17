package com.paytech.platform.support.service.processor.webhook;

import java.security.SecureRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.delivery.domain.RotateWebhookSecretCommand;
import com.paytech.platform.support.delivery.domain.RotateWebhookSecretResult;
import com.paytech.platform.support.delivery.domain.WebhookSignature;
import com.paytech.platform.support.delivery.integration.webhook.WebhookSecretVault;
import com.paytech.platform.support.delivery.service.WebhookManagementService;
import com.paytech.platform.support.service.processor.webhook.request.ClientWebhookRequest;
import com.paytech.platform.support.service.processor.webhook.response.RotateWebhookSecretResponse;

import lombok.AllArgsConstructor;

/**
 * 工作区成员自助轮换端点签名密钥
 * <li>锁 webhook-endpoint:{endpointId}；事务外先确认归属，再生成密钥并 Vault Protect。</li>
 * <li>旧密钥按配置宽限（默认 1 小时）继续参与签名，或立即作废；新密钥明文只在结果中出现一次。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"client.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ClientRotateWebhookSecretProcessor
    extends AbstractLockProcessor<ClientWebhookRequest, RotateWebhookSecretResponse> {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookSecretVault vault;
    private final ClientWebhookSupport support;
    private final WebhookManagementService management;

    @Override
    protected LockCommand lockCommand(ClientWebhookRequest request) {
        return LockCommand.tryLock("webhook-endpoint:" + request.getEndpointId(), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected RotateWebhookSecretResponse processWithinLock(ClientWebhookRequest request) {
        long endpointId = request.getEndpointId();
        // 事务外准备
        support.precheck(request.getAccountId(), request.getSessionId(), ClientDeveloperPermissions.WEBHOOK_MANAGE,
            endpointId);
        String secret = WebhookSignature.generateSecret(RANDOM);
        String token = vault.protect(secret);

        // 轮换与审计
        RotateWebhookSecretResult result = support.write(request.getAccountId(), request.getSessionId(),
            ClientDeveloperPermissions.WEBHOOK_MANAGE, (workspace, now) -> {
                support.requireEndpoint(workspace, endpointId);
                var rotated = management.rotate(new RotateWebhookSecretCommand(endpointId, token,
                    request.isImmediate() ? now : now.plus(support.secretGracePeriod()), now));
                support.record(workspace, "WEBHOOK_ENDPOINT", endpointId, "SECRET_ROTATED", now);
                return rotated;
            });
        return new RotateWebhookSecretResponse(result.getRotationNo(), secret, result.getPreviousValidUntil());
    }
}

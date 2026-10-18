package com.paytech.platform.support.service.processor.webhook;

import java.security.SecureRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.delivery.domain.CreateWebhookEndpointCommand;
import com.paytech.platform.support.delivery.domain.WebhookSignature;
import com.paytech.platform.support.delivery.integration.webhook.WebhookSecretVault;
import com.paytech.platform.support.delivery.service.WebhookManagementService;
import com.paytech.platform.support.service.processor.webhook.request.ClientWebhookRequest;
import com.paytech.platform.support.service.processor.webhook.response.CreateWebhookEndpointResponse;

import lombok.AllArgsConstructor;

/**
 * 工作区成员自助新建 Webhook 端点
 * <li>锁 webhook-client:{clientId}，与运营代配置新建同一互斥域，保护每个客户 5 个端点上限。</li>
 * <li>DNS 校验、密钥生成与 Vault Protect 在事务外；事务内重证工作区与权限后写入并记审计。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"client.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ClientCreateWebhookEndpointProcessor
    extends AbstractLockProcessor<ClientWebhookRequest, CreateWebhookEndpointResponse> {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookSecretVault vault;
    private final ClientWebhookSupport support;
    private final WebhookManagementService management;

    @Override
    protected LockCommand lockCommand(ClientWebhookRequest request) {
        var workspace = support.precheck(request.getAccountId(), request.getSessionId(),
            ClientDeveloperPermissions.WEBHOOK_MANAGE, null);
        return LockCommand.tryLock("webhook-client:" + workspace.getClientId(), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected CreateWebhookEndpointResponse processWithinLock(ClientWebhookRequest request) {
        // 事务外准备
        String url = support.requirePublicUrl(request.getUrl());
        String secret = WebhookSignature.generateSecret(RANDOM);
        String token = vault.protect(secret);

        // 写入与审计
        var change = support.write(request.getAccountId(), request.getSessionId(),
            ClientDeveloperPermissions.WEBHOOK_MANAGE, (workspace, now) -> {
                var created = management.createEndpoint(new CreateWebhookEndpointCommand(workspace.getTenantId(), url,
                    request.getEventTypes(), token, now));
                support.record(workspace, "WEBHOOK_ENDPOINT", created.getEndpointId(), ClientWebhookSupport.REASON, now);
                return created;
            });
        return new CreateWebhookEndpointResponse(change.getEndpointId(), change.getAfterVersion(), secret);
    }
}

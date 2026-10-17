package com.paytech.platform.support.service.processor.webhook;

import java.security.SecureRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.delivery.domain.CreateWebhookEndpointCommand;
import com.paytech.platform.support.delivery.domain.WebhookSignature;
import com.paytech.platform.support.delivery.integration.webhook.WebhookSecretVault;
import com.paytech.platform.support.delivery.service.WebhookManagementService;
import com.paytech.platform.support.service.processor.webhook.request.CreateWebhookEndpointRequest;
import com.paytech.platform.support.service.processor.webhook.response.CreateWebhookEndpointResponse;

import lombok.AllArgsConstructor;

/**
 * 新建端点并生成首个签名密钥
 * <li>先确认管理权限，再在事务外校验地址 DNS 并把密钥保存到 Vault；写事务内重检权限、写入并审计</li>
 * <li>同一 Client 的新建串行，保护每个 Client 的端点上限；密钥明文只在响应中返回一次</li>
 * <li>写事务失败时 Vault 中会留下未被引用的密钥记录，它不会被用于签名</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"operations.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class CreateWebhookEndpointProcessor
    extends AbstractLockProcessor<CreateWebhookEndpointRequest, CreateWebhookEndpointResponse> {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookSecretVault vault;
    private final WebhookManagementSupport support;
    private final WebhookManagementService management;

    @Override
    protected LockCommand lockCommand(CreateWebhookEndpointRequest request) {
        return LockCommand.tryLock(WebhookManagementSupport.clientLockKey(request.getClientId()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected CreateWebhookEndpointResponse processWithinLock(CreateWebhookEndpointRequest request) {
        // 事务外准备
        support.requireManage(request.getActor(), request.getSession());
        long tenantId = support.requireClientTenant(request.getClientId());
        String url = support.requirePublicUrl(request.getUrl());
        String secret = WebhookSignature.generateSecret(RANDOM);
        String token = vault.protect(secret);

        // 写入与审计
        var change = support.write(request.getActor(), request.getSession(), request.getReason(),
            (context, now) -> management.createEndpoint(new CreateWebhookEndpointCommand(tenantId, url,
                request.getEventTypes(), token, now)), result -> result);
        return new CreateWebhookEndpointResponse(change.getEndpointId(), change.getAfterVersion(), secret);
    }
}

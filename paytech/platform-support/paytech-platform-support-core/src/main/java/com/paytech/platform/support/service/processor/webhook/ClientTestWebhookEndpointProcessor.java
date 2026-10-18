package com.paytech.platform.support.service.processor.webhook;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.delivery.domain.WebhookAttemptOutcome;
import com.paytech.platform.support.delivery.domain.WebhookSignature;
import com.paytech.platform.support.delivery.integration.webhook.WebhookHttpSender;
import com.paytech.platform.support.delivery.integration.webhook.WebhookSecretVault;
import com.paytech.platform.support.delivery.service.WebhookManagementService;
import com.paytech.platform.support.service.processor.webhook.request.ClientWebhookRequest;

import lombok.AllArgsConstructor;

/**
 * 工作区成员自助向端点同步发送一次 webhook.ping 测试事件
 * <li>只读快照内确认工作区、权限与端点归属后，在事务外 Reveal 密钥、签名并发送；不写投递记录。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"client.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ClientTestWebhookEndpointProcessor extends AbstractProcessor<ClientWebhookRequest, WebhookAttemptOutcome> {

    private final WebhookHttpSender sender;
    private final WebhookSecretVault vault;
    private final ClientWebhookSupport support;
    private final WebhookManagementService management;

    @Override
    protected WebhookAttemptOutcome processRequest(ClientWebhookRequest request) {
        long endpointId = request.getEndpointId();
        var workspace = support.precheck(request.getAccountId(), request.getSessionId(),
            ClientDeveloperPermissions.WEBHOOK_MANAGE, endpointId);
        var now = support.now();
        var target = management.signingTarget(endpointId, now);
        var keys = target.getSecretVaultTokens().stream().map(vault::reveal).toList();
        byte[] body = TestWebhookEndpointProcessor.pingEnvelope(endpointId, now).getCanonicalBytes();
        var outcome = sender.send(URI.create(target.getUrl()), body,
            WebhookSignature.header(now.getEpochSecond(), body, keys));
        support.record(workspace, "WEBHOOK_ENDPOINT", endpointId,
            outcome.isSucceeded() ? "SUCCEEDED" : outcome.getFailureCode().getCode(), now);
        return outcome;
    }
}

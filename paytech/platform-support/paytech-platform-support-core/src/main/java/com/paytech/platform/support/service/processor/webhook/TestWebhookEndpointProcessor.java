package com.paytech.platform.support.service.processor.webhook;

import java.net.URI;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.enums.WebhookEventType;
import com.paytech.platform.support.delivery.domain.WebhookAttemptOutcome;
import com.paytech.platform.support.delivery.domain.WebhookEnvelope;
import com.paytech.platform.support.delivery.domain.WebhookSignature;
import com.paytech.platform.support.delivery.integration.webhook.WebhookHttpSender;
import com.paytech.platform.support.delivery.integration.webhook.WebhookSecretVault;
import com.paytech.platform.support.delivery.service.WebhookManagementService;
import com.paytech.platform.support.service.processor.webhook.request.TestWebhookEndpointRequest;

import lombok.AllArgsConstructor;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 同步发送一次签名的 webhook.ping 测试事件
 * <li>确认管理权限后读取地址与密钥引用，Vault 与 HTTP 不在事务内；发送后另开短事务重检权限并记录审计（结果代码）</li>
 * <li>与正式投递使用同一发送器、SSRF 防护与签名；不落事件或投递记录，停用的端点也可测试</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"operations.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class TestWebhookEndpointProcessor extends AbstractProcessor<TestWebhookEndpointRequest, WebhookAttemptOutcome> {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final WebhookHttpSender sender;
    private final WebhookSecretVault vault;
    private final WebhookManagementSupport support;
    private final WebhookManagementService management;

    @Override
    protected WebhookAttemptOutcome processRequest(TestWebhookEndpointRequest request) {
        var now = support.now();
        support.requireManage(request.getActor(), request.getSession());
        var target = management.signingTarget(request.getEndpointId(), now);
        var keys = target.getSecretVaultTokens().stream().map(vault::reveal).toList();
        byte[] body = pingEnvelope(request.getEndpointId(), now).getCanonicalBytes();
        var outcome = sender.send(URI.create(target.getUrl()), body,
            WebhookSignature.header(now.getEpochSecond(), body, keys));
        String result = outcome.isSucceeded() ? "SUCCEEDED" : outcome.getFailureCode().getCode();
        support.transaction(request.getActor(), request.getSession(), (context, at) -> {
            support.recordSucceeded(request.getActor(), context.member().tenantId(), "WEBHOOK_ENDPOINT",
                request.getEndpointId(), result, now);
            return null;
        });
        return outcome;
    }

    /** 测试事件：随机 eventId，data.object 只含端点 ID。 */
    static WebhookEnvelope pingEnvelope(long endpointId, java.time.Instant now) {
        ObjectNode envelope = JSON.createObjectNode();
        envelope.put("eventId", "evt_test_" + UUID.randomUUID().toString().replace("-", ""));
        envelope.put("eventType", WebhookEventType.WEBHOOK_PING.getCode());
        envelope.put("schemaVersion", "1");
        envelope.put("createdAt", now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
        envelope.putObject("data").putObject("object").put("endpointId", Long.toString(endpointId));
        return WebhookEnvelope.parse(envelope);
    }
}

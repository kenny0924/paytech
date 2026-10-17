package com.paytech.platform.support.service.processor.webhook;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.delivery.domain.WebhookEndpointView;
import com.paytech.platform.support.delivery.service.WebhookQueryService;
import com.paytech.platform.support.service.processor.webhook.request.ClientWebhookRequest;

import lombok.AllArgsConstructor;

/** 本工作区全部端点与当前签名密钥概况；不含密钥值。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"client.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ClientWebhookEndpointsProcessor extends AbstractProcessor<ClientWebhookRequest, List<WebhookEndpointView>> {
    private final WebhookQueryService query;
    private final ClientWebhookSupport support;

    @Override
    protected List<WebhookEndpointView> processRequest(ClientWebhookRequest request) {
        return support.read(request.getAccountId(), request.getSessionId(), ClientDeveloperPermissions.WEBHOOK_READ,
            workspace -> query.endpoints(workspace.getTenantId(), support.now()));
    }
}

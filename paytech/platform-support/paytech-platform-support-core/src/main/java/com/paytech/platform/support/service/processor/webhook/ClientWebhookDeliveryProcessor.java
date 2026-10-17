package com.paytech.platform.support.service.processor.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryDetailView;
import com.paytech.platform.support.delivery.service.WebhookQueryService;
import com.paytech.platform.support.service.processor.webhook.request.ClientWebhookRequest;

import lombok.AllArgsConstructor;

/** 投递详情：同一快照内读取并确认其端点属于当前工作区，否则 404。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"client.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ClientWebhookDeliveryProcessor extends AbstractProcessor<ClientWebhookRequest, WebhookDeliveryDetailView> {

    private final WebhookQueryService query;
    private final ClientWebhookSupport support;

    @Override
    protected WebhookDeliveryDetailView processRequest(ClientWebhookRequest request) {
        return support.read(request.getAccountId(), request.getSessionId(), ClientDeveloperPermissions.WEBHOOK_READ,
            workspace -> {
                var detail = query.delivery(request.getDeliveryId());
                support.requireEndpoint(workspace, detail.getEndpointId());
                return detail;
            });
    }
}

package com.paytech.platform.support.service.processor.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryFilter;
import com.paytech.platform.support.delivery.domain.WebhookDeliverySummaryView;
import com.paytech.platform.support.delivery.service.WebhookQueryService;
import com.paytech.platform.support.service.processor.webhook.request.ClientWebhookRequest;

import lombok.AllArgsConstructor;

/** 本工作区投递分页；租户来自会话，其余条件原样传递。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"client.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ClientWebhookDeliveriesProcessor
    extends AbstractProcessor<ClientWebhookRequest, PagingResponseBody<WebhookDeliverySummaryView>> {

    private final WebhookQueryService query;
    private final ClientWebhookSupport support;

    @Override
    protected PagingResponseBody<WebhookDeliverySummaryView> processRequest(ClientWebhookRequest request) {
        return support.read(request.getAccountId(), request.getSessionId(), ClientDeveloperPermissions.WEBHOOK_READ,
            workspace -> query.deliveries(new WebhookDeliveryFilter(workspace.getTenantId(), request.getCreatedFrom(),
                request.getCreatedTo(), request.getEndpointId(), request.getEventType(), request.getDeliveryStatus(), null),
                request.getPaging()));
    }
}

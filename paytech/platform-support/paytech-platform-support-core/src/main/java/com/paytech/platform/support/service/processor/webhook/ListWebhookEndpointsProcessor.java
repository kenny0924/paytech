package com.paytech.platform.support.service.processor.webhook;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.webhookmanagement.WebhookManagementPermissions;
import com.paytech.platform.support.delivery.domain.WebhookEndpointView;
import com.paytech.platform.support.delivery.service.WebhookQueryService;
import com.paytech.platform.support.service.processor.webhook.request.ListWebhookEndpointsRequest;

import lombok.AllArgsConstructor;

/** 查询 Client 的全部端点及签名密钥概况；不返回密钥值。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"operations.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ListWebhookEndpointsProcessor
    extends AbstractProcessor<ListWebhookEndpointsRequest, List<WebhookEndpointView>> {

    private final WebhookQueryService query;
    private final WebhookManagementSupport support;

    @Override
    protected List<WebhookEndpointView> processRequest(ListWebhookEndpointsRequest request) {
        return support.read(request.getActor(), request.getSession(), WebhookManagementPermissions.READ,
            () -> query.endpoints(support.requireClientTenant(request.getClientId()), support.now()));
    }
}

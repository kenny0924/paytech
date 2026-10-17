package com.paytech.platform.support.service.processor.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.webhookmanagement.WebhookManagementPermissions;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryFilter;
import com.paytech.platform.support.delivery.domain.WebhookDeliverySummaryView;
import com.paytech.platform.support.delivery.service.WebhookQueryService;
import com.paytech.platform.support.service.processor.webhook.request.ListWebhookDeliveriesRequest;

import lombok.AllArgsConstructor;

/** 按创建时间倒序分页查询 Client 的投递。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"operations.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ListWebhookDeliveriesProcessor
    extends AbstractProcessor<ListWebhookDeliveriesRequest, PagingResponseBody<WebhookDeliverySummaryView>> {

    private final WebhookQueryService query;
    private final WebhookManagementSupport support;

    @Override
    protected PagingResponseBody<WebhookDeliverySummaryView> processRequest(ListWebhookDeliveriesRequest request) {
        return support.read(request.getActor(), request.getSession(), WebhookManagementPermissions.READ,
            () -> query.deliveries(filter(request, support.requireClientTenant(request.getClientId())),
                request.getPaging()));
    }

    /** 租户由 Client 解析，其余条件原样传递。 */
    static WebhookDeliveryFilter filter(ListWebhookDeliveriesRequest request, long tenantId) {
        return new WebhookDeliveryFilter(tenantId, request.getCreatedFrom(), request.getCreatedTo(),
            request.getEndpointId(), request.getEventType(), request.getStatus(), request.getEventId());
    }
}

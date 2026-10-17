package com.paytech.platform.support.service.processor.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.webhookmanagement.WebhookManagementPermissions;
import com.paytech.platform.support.delivery.domain.WebhookDeliveryDetailView;
import com.paytech.platform.support.delivery.service.WebhookQueryService;
import com.paytech.platform.support.service.processor.webhook.request.GetWebhookDeliveryRequest;

import lombok.AllArgsConstructor;

/** 读取投递、冻结事件与全部尝试；不存在返回 404。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"operations.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class GetWebhookDeliveryProcessor extends AbstractProcessor<GetWebhookDeliveryRequest, WebhookDeliveryDetailView> {

    private final WebhookQueryService query;
    private final WebhookManagementSupport support;

    @Override
    protected WebhookDeliveryDetailView processRequest(GetWebhookDeliveryRequest request) {
        return support.read(request.getActor(), request.getSession(), WebhookManagementPermissions.READ,
            () -> query.delivery(request.getDeliveryId()));
    }
}

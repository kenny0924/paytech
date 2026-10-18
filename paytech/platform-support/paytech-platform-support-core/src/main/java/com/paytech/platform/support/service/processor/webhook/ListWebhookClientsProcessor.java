package com.paytech.platform.support.service.processor.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.ClientTenant;
import com.paytech.platform.support.accesscontrol.domain.webhookmanagement.WebhookManagementPermissions;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientTenantQueryService;
import com.paytech.platform.support.delivery.service.WebhookQueryService;
import com.paytech.platform.support.service.processor.webhook.request.ListWebhookClientsRequest;
import com.paytech.platform.support.service.processor.webhook.response.WebhookClientResponse;

import lombok.AllArgsConstructor;

/**
 * 按 Client ID 或名称搜索 Client，并附带各自的端点数量
 * <li>租户由 Access Control 查询、端点数量由 Delivery 统计，两次本地查询由此组合，不跨模块联表</li>
 * <li>会话权限、租户分页与端点计数在同一只读快照中读取</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"operations.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ListWebhookClientsProcessor
    extends AbstractProcessor<ListWebhookClientsRequest, PagingResponseBody<WebhookClientResponse>> {

    private final WebhookQueryService query;
    private final ClientTenantQueryService tenants;
    private final WebhookManagementSupport support;

    @Override
    protected PagingResponseBody<WebhookClientResponse> processRequest(ListWebhookClientsRequest request) {
        return support.read(request.getActor(), request.getSession(), WebhookManagementPermissions.READ, () -> {
            var clients = tenants.searchClients(request.getKeyword(), request.getPaging());
            var counts = query.endpointCounts(clients.items().stream().map(ClientTenant::getTenantId).toList());
            return clients.map(client -> new WebhookClientResponse(client,
                counts.getOrDefault(client.getTenantId(), 0)));
        });
    }
}

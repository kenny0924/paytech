package com.paytech.platform.support.service.processor.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.delivery.domain.ChangeWebhookEndpointStatusCommand;
import com.paytech.platform.support.delivery.domain.UpdateWebhookEndpointCommand;
import com.paytech.platform.support.delivery.domain.WebhookConfigurationChange;
import com.paytech.platform.support.delivery.service.WebhookManagementService;
import com.paytech.platform.support.service.processor.webhook.request.ClientWebhookRequest;

import lombok.AllArgsConstructor;

/**
 * 工作区成员自助修改端点地址与订阅，或启用 / 停用端点
 * <li>status 为空表示修改地址与订阅；否则只改状态。</li>
 * <li>锁 webhook-endpoint:{endpointId}，与运营入口同一互斥域；端点不属于当前工作区返回 404。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"client.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ClientUpdateWebhookEndpointProcessor
    extends AbstractLockProcessor<ClientWebhookRequest, WebhookConfigurationChange> {

    private final ClientWebhookSupport support;
    private final WebhookManagementService management;

    @Override
    protected LockCommand lockCommand(ClientWebhookRequest request) {
        return LockCommand.tryLock("webhook-endpoint:" + request.getEndpointId(), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected WebhookConfigurationChange processWithinLock(ClientWebhookRequest request) {
        long endpointId = request.getEndpointId();
        // 事务外：先确认归属再解析 DNS，避免为其他工作区的端点触发外部调用
        String url = null;
        if (request.getStatus() == null) {
            support.precheck(request.getAccountId(), request.getSessionId(), ClientDeveloperPermissions.WEBHOOK_MANAGE,
                endpointId);
            url = support.requirePublicUrl(request.getUrl());
        }
        String checkedUrl = url;
        return support.write(request.getAccountId(), request.getSessionId(), ClientDeveloperPermissions.WEBHOOK_MANAGE,
            (workspace, now) -> {
                support.requireEndpoint(workspace, endpointId);
                var change = request.getStatus() == null
                    ? management.updateEndpoint(new UpdateWebhookEndpointCommand(endpointId, checkedUrl,
                        request.getEventTypes(), request.getExpectedVersion(), now))
                    : management.changeStatus(new ChangeWebhookEndpointStatusCommand(endpointId, request.getStatus(),
                        request.getExpectedVersion(), now));
                support.record(workspace, "WEBHOOK_ENDPOINT", endpointId, ClientWebhookSupport.REASON, now);
                return change;
            });
    }
}

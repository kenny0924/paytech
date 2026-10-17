package com.paytech.platform.support.service.processor.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.delivery.domain.ReplayWebhookDeliveryCommand;
import com.paytech.platform.support.delivery.service.WebhookManagementService;
import com.paytech.platform.support.service.processor.webhook.request.ClientWebhookRequest;

import lombok.AllArgsConstructor;

/**
 * 工作区成员自助重发单条投递：事务内确认投递属于当前工作区后生成新投递并记审计
 * <li>请求人记录为客户账号；事件 ID 与内容不变，新投递由补偿任务发送。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"client.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ClientReplayWebhookDeliveryProcessor extends AbstractProcessor<ClientWebhookRequest, Long> {

    private final ClientWebhookSupport support;
    private final WebhookManagementService management;

    @Override
    protected Long processRequest(ClientWebhookRequest request) {
        long deliveryId = request.getDeliveryId();
        return support.write(request.getAccountId(), request.getSessionId(), ClientDeveloperPermissions.WEBHOOK_MANAGE,
            (workspace, now) -> {
                support.requireDelivery(workspace, deliveryId);
                long replayId = management.replay(new ReplayWebhookDeliveryCommand(deliveryId, workspace.getAccountId(), now));
                support.record(workspace, "WEBHOOK_DELIVERY", deliveryId, ClientWebhookSupport.REASON, now);
                return replayId;
            });
    }
}

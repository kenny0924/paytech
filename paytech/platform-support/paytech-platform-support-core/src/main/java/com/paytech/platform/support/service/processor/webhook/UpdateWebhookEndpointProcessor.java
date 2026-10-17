package com.paytech.platform.support.service.processor.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.delivery.domain.UpdateWebhookEndpointCommand;
import com.paytech.platform.support.delivery.domain.WebhookConfigurationChange;
import com.paytech.platform.support.delivery.service.WebhookManagementService;
import com.paytech.platform.support.service.processor.webhook.request.UpdateWebhookEndpointRequest;

import lombok.AllArgsConstructor;

/**
 * 修改端点地址与订阅
 * <li>先确认管理权限并在事务外校验地址 DNS；写事务内重检权限、按版本写入并审计</li>
 * <li>尚未完成的投递在下一次尝试时使用新地址；取消订阅的类型在被取出时取消</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"operations.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class UpdateWebhookEndpointProcessor
    extends AbstractLockProcessor<UpdateWebhookEndpointRequest, WebhookConfigurationChange> {

    private final WebhookManagementSupport support;
    private final WebhookManagementService management;

    @Override
    protected LockCommand lockCommand(UpdateWebhookEndpointRequest request) {
        return LockCommand.tryLock(WebhookManagementSupport.endpointLockKey(request.getEndpointId()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected WebhookConfigurationChange processWithinLock(UpdateWebhookEndpointRequest request) {
        support.requireManage(request.getActor(), request.getSession());
        String url = support.requirePublicUrl(request.getUrl());
        return support.write(request.getActor(), request.getSession(), request.getReason(),
            (context, now) -> management.updateEndpoint(new UpdateWebhookEndpointCommand(request.getEndpointId(), url,
                request.getEventTypes(), request.getExpectedVersion(), now)), change -> change);
    }
}

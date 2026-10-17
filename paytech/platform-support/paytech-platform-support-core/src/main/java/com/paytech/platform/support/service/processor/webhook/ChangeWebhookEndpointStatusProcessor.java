package com.paytech.platform.support.service.processor.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.delivery.domain.ChangeWebhookEndpointStatusCommand;
import com.paytech.platform.support.delivery.domain.WebhookConfigurationChange;
import com.paytech.platform.support.delivery.service.WebhookManagementService;
import com.paytech.platform.support.service.processor.webhook.request.ChangeWebhookEndpointStatusRequest;

import lombok.AllArgsConstructor;

/**
 * 启用或停用端点
 * <li>鉴权、写入与配置审计同一事务；版本不符返回 409，状态未变化返回 400</li>
 * <li>停用后未完成的投递被取出时取消；重新启用不自动补发</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = {"operations.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ChangeWebhookEndpointStatusProcessor
    extends AbstractLockProcessor<ChangeWebhookEndpointStatusRequest, WebhookConfigurationChange> {

    private final WebhookManagementSupport support;
    private final WebhookManagementService management;

    @Override
    protected LockCommand lockCommand(ChangeWebhookEndpointStatusRequest request) {
        return LockCommand.tryLock(WebhookManagementSupport.endpointLockKey(request.getEndpointId()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected WebhookConfigurationChange processWithinLock(ChangeWebhookEndpointStatusRequest request) {
        return support.write(request.getActor(), request.getSession(), request.getReason(),
            (context, now) -> management.changeStatus(new ChangeWebhookEndpointStatusCommand(request.getEndpointId(),
                request.getStatus(), request.getExpectedVersion(), now)), change -> change);
    }
}

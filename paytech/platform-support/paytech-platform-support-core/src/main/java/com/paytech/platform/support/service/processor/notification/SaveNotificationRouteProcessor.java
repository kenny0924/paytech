package com.paytech.platform.support.service.processor.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.delivery.domain.NotificationConfigurationChange;
import com.paytech.platform.support.delivery.domain.SaveNotificationRouteCommand;
import com.paytech.platform.support.delivery.service.NotificationManagementService;
import com.paytech.platform.support.service.processor.notification.request.SaveNotificationRouteRequest;

import lombok.AllArgsConstructor;

/**
 * 新建或修改通知模板路由的主备账号；新建的路由为启用状态
 * <li>主备账号必须是启用的 EMAIL 账号且互不相同；版本不符返回 409</li>
 * <li>账号不加锁：与账号停用并发时可能保存到刚停用的账号，发送时会以账号未启用失败，不会误发</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class SaveNotificationRouteProcessor
    extends AbstractLockProcessor<SaveNotificationRouteRequest, NotificationConfigurationChange> {

    private final NotificationManagementSupport support;
    private final NotificationManagementService management;

    /** 按模板编码协调，与同一模板的启停、内容修改互斥。 */
    @Override
    protected LockCommand lockCommand(SaveNotificationRouteRequest request) {
        return LockCommand.tryLock(NotificationManagementSupport.templateLockKey(request.getTemplateCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected NotificationConfigurationChange processWithinLock(SaveNotificationRouteRequest request) {
        return support.write(request.getActor(), request.getSession(), "NOTIFICATION_ROUTE",
            request.getTemplateCode(), request.getReason(),
            now -> management.saveRoute(new SaveNotificationRouteCommand(request.getTemplateCode(),
                request.getPrimaryProviderCode(), request.getFallbackProviderCode(), request.getExpectedVersion(),
                now)));
    }
}

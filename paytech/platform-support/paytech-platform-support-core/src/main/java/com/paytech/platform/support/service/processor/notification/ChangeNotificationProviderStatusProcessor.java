package com.paytech.platform.support.service.processor.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.delivery.domain.ChangeNotificationStatusCommand;
import com.paytech.platform.support.delivery.domain.NotificationConfigurationChange;
import com.paytech.platform.support.delivery.service.NotificationManagementService;
import com.paytech.platform.support.service.processor.notification.request.ChangeNotificationStatusRequest;

import lombok.AllArgsConstructor;

/**
 * 启用或停用发送账号；不检查引用，由页面提示受影响的路由
 * <li>鉴权、写入与配置审计同一事务；版本不符返回 409，状态未变化返回 400</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangeNotificationProviderStatusProcessor
    extends AbstractLockProcessor<ChangeNotificationStatusRequest, NotificationConfigurationChange> {

    private final NotificationManagementSupport support;
    private final NotificationManagementService management;

    /** 按账号编码协调。 */
    @Override
    protected LockCommand lockCommand(ChangeNotificationStatusRequest request) {
        return LockCommand.tryLock(NotificationManagementSupport.providerLockKey(request.getCode()), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected NotificationConfigurationChange processWithinLock(ChangeNotificationStatusRequest request) {
        return support.write(request.getActor(), request.getSession(), "NOTIFICATION_PROVIDER", request.getCode(),
            request.getReason(), now -> management.changeProviderStatus(new ChangeNotificationStatusCommand(request.getCode(),
                request.getStatus(), request.getExpectedVersion(), now)));
    }
}

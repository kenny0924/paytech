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
 * 启用或停用通知模板；启用前至少要有一条语言内容
 * <li>鉴权、写入与配置审计同一事务；版本不符返回 409，状态未变化返回 400</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangeNotificationTemplateStatusProcessor
    extends AbstractLockProcessor<ChangeNotificationStatusRequest, NotificationConfigurationChange> {

    private final NotificationManagementSupport support;
    private final NotificationManagementService management;

    /** 按模板编码协调，与同一模板的内容、路由修改互斥。 */
    @Override
    protected LockCommand lockCommand(ChangeNotificationStatusRequest request) {
        return LockCommand.tryLock(NotificationManagementSupport.templateLockKey(request.getCode()), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected NotificationConfigurationChange processWithinLock(ChangeNotificationStatusRequest request) {
        return support.write(request.getActor(), request.getSession(), "NOTIFICATION_TEMPLATE", request.getCode(),
            request.getReason(), now -> management.changeTemplateStatus(new ChangeNotificationStatusCommand(request.getCode(),
                request.getStatus(), request.getExpectedVersion(), now)));
    }
}

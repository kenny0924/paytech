package com.paytech.platform.support.service.processor.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.delivery.domain.NotificationConfigurationChange;
import com.paytech.platform.support.delivery.service.NotificationManagementService;
import com.paytech.platform.support.service.processor.notification.request.CreateNotificationTemplateRequest;

import lombok.AllArgsConstructor;

/**
 * 新建停用状态的 EMAIL 通知模板
 * <li>鉴权、写入与配置审计同一事务；编码已存在返回 400</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateNotificationTemplateProcessor
    extends AbstractLockProcessor<CreateNotificationTemplateRequest, NotificationConfigurationChange> {

    private final NotificationManagementSupport support;
    private final NotificationManagementService management;

    /** 按模板编码协调，与同一模板的内容、路由修改互斥。 */
    @Override
    protected LockCommand lockCommand(CreateNotificationTemplateRequest request) {
        return LockCommand.tryLock(NotificationManagementSupport.templateLockKey(request.getTemplateCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected NotificationConfigurationChange processWithinLock(CreateNotificationTemplateRequest request) {
        return support.write(request.getActor(), request.getSession(), "NOTIFICATION_TEMPLATE",
            request.getTemplateCode(), request.getReason(), now -> management.createTemplate(request.getTemplateCode(), now));
    }
}

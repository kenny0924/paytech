package com.paytech.platform.support.service.processor.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.delivery.domain.NotificationConfigurationChange;
import com.paytech.platform.support.delivery.domain.SaveNotificationProviderCommand;
import com.paytech.platform.support.delivery.service.NotificationManagementService;
import com.paytech.platform.support.service.processor.notification.request.SaveNotificationProviderRequest;

import lombok.AllArgsConstructor;

/**
 * 新建停用状态的发送账号
 * <li>鉴权、写入与配置审计同一事务；编码已存在返回 400，发件地址格式不符返回 400</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateNotificationProviderProcessor
    extends AbstractLockProcessor<SaveNotificationProviderRequest, NotificationConfigurationChange> {

    private final NotificationManagementSupport support;
    private final NotificationManagementService management;

    /** 按账号编码协调，与同一账号的编辑、换密钥、启停互斥。 */
    @Override
    protected LockCommand lockCommand(SaveNotificationProviderRequest request) {
        return LockCommand.tryLock(NotificationManagementSupport.providerLockKey(request.getProviderCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected NotificationConfigurationChange processWithinLock(SaveNotificationProviderRequest request) {
        return support.write(request.getActor(), request.getSession(), "NOTIFICATION_PROVIDER",
            request.getProviderCode(), request.getReason(),
            now -> management.createProvider(new SaveNotificationProviderCommand(request.getProviderCode(),
                request.getProviderType(), request.getRegion(), request.getFromAddress(),
                request.getConfigurationSet(), 0, now)));
    }
}

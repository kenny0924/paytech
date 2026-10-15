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
 * 修改发送账号的区域、发件地址与 Configuration Set；立即作用于之后的发送
 * <li>鉴权、写入与配置审计同一事务；版本不符返回 409，不检查引用，由页面提示影响</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateNotificationProviderProcessor
    extends AbstractLockProcessor<SaveNotificationProviderRequest, NotificationConfigurationChange> {

    private final NotificationManagementSupport support;
    private final NotificationManagementService management;

    /** 按账号编码协调，与同一账号的换密钥、启停互斥。 */
    @Override
    protected LockCommand lockCommand(SaveNotificationProviderRequest request) {
        return LockCommand.tryLock(NotificationManagementSupport.providerLockKey(request.getProviderCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected NotificationConfigurationChange processWithinLock(SaveNotificationProviderRequest request) {
        return support.write(request.getActor(), request.getSession(), "NOTIFICATION_PROVIDER",
            request.getProviderCode(), request.getReason(),
            now -> management.updateProvider(new SaveNotificationProviderCommand(request.getProviderCode(), null,
                request.getRegion(), request.getFromAddress(), request.getConfigurationSet(),
                request.getExpectedVersion(), now)));
    }
}

package com.paytech.platform.support.service.processor.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domain.notificationmanagement.NotificationManagementPermissions;
import com.paytech.platform.support.delivery.domain.NotificationConfigurationChange;
import com.paytech.platform.support.delivery.domain.ReplaceNotificationCredentialCommand;
import com.paytech.platform.support.delivery.service.NotificationManagementService;
import com.paytech.platform.support.delivery.service.processor.ProtectNotificationCredentialProcessor;
import com.paytech.platform.support.delivery.service.processor.request.ProtectNotificationCredentialRequest;
import com.paytech.platform.support.service.processor.notification.request.ReplaceNotificationCredentialRequest;

import lombok.AllArgsConstructor;

/**
 * 录入或更换发送账号密钥：先存入 Vault，再以短事务替换账号的 Token
 * <li>Vault 调用在数据库事务外；鉴权与版本检查在其之前，写入时按版本再次保护</li>
 * <li>Vault 失败账号不变；Vault 成功但版本冲突返回 409，新密文不再被引用（Vault V1 无 Retire）</li>
 * <li>密钥只在内存中传递，不进入日志、审计与响应</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ReplaceNotificationCredentialProcessor
    extends AbstractLockProcessor<ReplaceNotificationCredentialRequest, NotificationConfigurationChange> {

    private final NotificationManagementSupport support;
    private final NotificationManagementService management;
    private final ProtectNotificationCredentialProcessor protect;

    /** 按账号编码协调，锁覆盖 Vault 调用，与同一账号的编辑、启停互斥。 */
    @Override
    protected LockCommand lockCommand(ReplaceNotificationCredentialRequest request) {
        return LockCommand.tryLock(NotificationManagementSupport.providerLockKey(request.getProviderCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected NotificationConfigurationChange processWithinLock(ReplaceNotificationCredentialRequest request) {
        // 鉴权与版本检查
        long providerId = support.precheck(request.getActor(), request.getSession(),
            NotificationManagementPermissions.CREDENTIAL_MANAGE,
            () -> management.requireCredentialReplaceable(request.getProviderCode(), request.getExpectedVersion()));

        // 事务外存入 Vault
        String token = protect.process(new ProtectNotificationCredentialRequest(providerId, request.getAccessKeyId(),
            request.getSecretAccessKey())).getCredentialToken();

        // 短事务替换 Token 并审计
        return support.write(request.getActor(), request.getSession(),
            NotificationManagementPermissions.CREDENTIAL_MANAGE, "NOTIFICATION_PROVIDER", request.getProviderCode(),
            request.getReason(), now -> management.replaceCredential(new ReplaceNotificationCredentialCommand(
                request.getProviderCode(), token, request.getExpectedVersion(), now)));
    }
}

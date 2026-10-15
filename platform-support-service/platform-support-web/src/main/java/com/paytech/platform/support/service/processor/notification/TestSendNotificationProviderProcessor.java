package com.paytech.platform.support.service.processor.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.notificationmanagement.NotificationManagementPermissions;
import com.paytech.platform.support.delivery.service.NotificationManagementService;
import com.paytech.platform.support.delivery.service.processor.SendProviderTestEmailProcessor;
import com.paytech.platform.support.delivery.service.processor.request.SendProviderTestEmailRequest;
import com.paytech.platform.support.delivery.service.processor.response.SendProviderTestEmailResponse;
import com.paytech.platform.support.service.processor.notification.request.TestSendNotificationProviderRequest;

import lombok.AllArgsConstructor;

/**
 * 用指定账号发送一封固定内容的测试邮件，并记审计
 * <li>鉴权在前；Vault 与服务商调用在数据库事务外；之后以短事务追加审计（结果码、收件邮箱摘要）</li>
 * <li>不加资源锁：不修改账号，与换密钥并发时使用调用时读到的密钥</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class TestSendNotificationProviderProcessor
    extends AbstractProcessor<TestSendNotificationProviderRequest, SendProviderTestEmailResponse> {

    private final NotificationManagementSupport support;
    private final NotificationManagementService management;
    private final SendProviderTestEmailProcessor testSend;

    @Override
    protected SendProviderTestEmailResponse processRequest(TestSendNotificationProviderRequest request) {
        // 鉴权
        support.requirePermission(request.getActor(), request.getSession(),
            NotificationManagementPermissions.CREDENTIAL_MANAGE);

        // 事务外发送
        var result = testSend.process(new SendProviderTestEmailRequest(request.getProviderCode(), request.getEmail()));

        // 审计
        support.write(request.getActor(), request.getSession(), NotificationManagementPermissions.CREDENTIAL_MANAGE,
            "NOTIFICATION_PROVIDER", request.getProviderCode(), request.getReason(),
            now -> management.testSendRecord(request.getProviderCode(), result.getRecipient(), result.getCode()));
        return result;
    }
}

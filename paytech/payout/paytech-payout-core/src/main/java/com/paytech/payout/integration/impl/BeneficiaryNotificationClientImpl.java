package com.paytech.payout.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.core.configuration.PayoutNotificationProperties;
import com.paytech.payout.domain.BeneficiaryNotification;
import com.paytech.payout.integration.BeneficiaryNotificationClient;
import com.paytech.platform.support.api.notification.NotificationCommandApi;
import com.paytech.platform.support.api.notification.request.SubmitNotificationRequestBody;
import com.paytech.platform.support.api.notification.response.SubmitNotificationResponseBody;

import lombok.AllArgsConstructor;

/** 经 Platform 通知接口（NotificationCommandApi）提交 tenantRecipients 通知；远端 DTO 不越过本边界。 */
@Component
@AllArgsConstructor
public class BeneficiaryNotificationClientImpl implements BeneficiaryNotificationClient {
    private final NotificationCommandApi notificationCommandApi;
    private final PayoutNotificationProperties notificationProperties;

    /** 提交通知并返回投递数。 */
    @Override
    public int submit(BeneficiaryNotification notification) {
        SubmitNotificationResponseBody response = notificationCommandApi.submitNotification(
            notificationProperties.getCallerService(), toRequestBody(notification));
        return response == null ? 0 : response.getDeliveryCount();
    }

    /** 领域通知转 Platform 请求：Local Partner 租户类型为 PARTNER；不传显式 recipients 与语言覆盖。 */
    static SubmitNotificationRequestBody toRequestBody(BeneficiaryNotification notification) {
        String tenantType = notification.getOwnerType() == BeneficiaryOwnerType.CLIENT ? "CLIENT" : "PARTNER";
        SubmitNotificationRequestBody.TenantRecipients recipients = new SubmitNotificationRequestBody.TenantRecipients(
            tenantType, notification.getOwnerId().toString(), notification.getPermissionCode(), null);
        return new SubmitNotificationRequestBody(null, notification.getSourceEventKey(), notification.getTemplateCode(),
            notification.getDefaultLocale(), notification.getParameters(), null, recipients);
    }
}

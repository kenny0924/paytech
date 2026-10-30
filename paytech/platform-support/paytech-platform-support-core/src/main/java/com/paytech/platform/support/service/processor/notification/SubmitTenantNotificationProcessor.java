package com.paytech.platform.support.service.processor.notification;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantPermissionRecipientView;
import com.paytech.platform.support.accesscontrol.domainservice.service.TenantPermissionRecipientService;
import com.paytech.platform.support.delivery.service.processor.SubmitNotificationProcessor;
import com.paytech.platform.support.delivery.service.processor.request.SubmitNotificationRequest;
import com.paytech.platform.support.service.processor.notification.request.SubmitTenantNotificationRequest;
import com.paytech.platform.support.service.processor.notification.response.SubmitTenantNotificationResponse;

import lombok.AllArgsConstructor;

/**
 * 按租户权限解析收件人后受理普通通知（payout S1 技术方案 8.3）
 * <li>受理事务前在本库解析：租户（类型 + 所有者引用）→ ACTIVE 成员 → 当前有效角色 → ACTIVE 角色权限含该权限 → ACTIVE 账号的已验证登录邮箱。</li>
 * <li>每人一条投递，直接复用账号登录邮箱 Token（EMAIL_ADDRESS_V1 STABLE，与 delivery 保护的地址同一数据类型），不 Reveal、不再 Protect。</li>
 * <li>0 人时不调用 delivery、不落库、不占用 sourceEventKey；超过 100 人整单拒绝。其余受理规则（来源去重、模板、语言、参数）由 delivery 入口负责。</li>
 * <li>解析与受理之间成员或权限的变化不再重证：以解析时刻的成员集合受理，发送时不复核权限。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class SubmitTenantNotificationProcessor
    extends AbstractProcessor<SubmitTenantNotificationRequest, SubmitTenantNotificationResponse> {

    /** 单次通知收件人上限，与显式收件人一致。 */
    static final int MAX_RECIPIENTS = 100;

    private final SubmitNotificationProcessor notifications;
    private final TenantPermissionRecipientService recipients;

    @Override
    protected SubmitTenantNotificationResponse processRequest(SubmitTenantNotificationRequest request) {
        // 解析收件人
        List<TenantPermissionRecipientView> holders = recipients.findRecipients(request.getTenantType(),
            request.getOwnerRefId(), request.getPermissionCode(), MAX_RECIPIENTS + 1);
        if (holders.isEmpty()) {
            return new SubmitTenantNotificationResponse(null, 0);
        }
        if (holders.size() > MAX_RECIPIENTS) {
            throw new BizCheckFailException("租户内具备该权限的收件成员超过 " + MAX_RECIPIENTS + " 人");
        }

        // 受理
        SubmitNotificationRequest notification = request.getNotification();
        notification.setRecipients(holders.stream().map(holder -> recipient(holder, request.getLocale())).toList());
        var accepted = notifications.process(notification);
        return new SubmitTenantNotificationResponse(accepted.getRequestId(), accepted.getAcceptedCount());
    }

    /** 以账号登录邮箱 Token 构造收件人；参数不按人覆盖。 */
    static SubmitNotificationRequest.Recipient recipient(TenantPermissionRecipientView holder, String locale) {
        var recipient = new SubmitNotificationRequest.Recipient();
        recipient.setAddressVaultToken(holder.getEmailVaultToken());
        recipient.setLocale(locale);
        return recipient;
    }
}

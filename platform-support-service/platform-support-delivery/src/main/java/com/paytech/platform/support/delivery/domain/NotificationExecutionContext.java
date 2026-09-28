package com.paytech.platform.support.delivery.domain;

import com.paytech.platform.support.delivery.core.dal.dataobject.*;
import com.paytech.platform.support.delivery.core.enums.*;
import com.paytech.foundation.common.error.BizCheckFailException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 从一致性读取构造的单个收件人执行上下文；终态无需配置，原发送查询只读取原 Provider。 */
@Getter
@RequiredArgsConstructor
public class NotificationExecutionContext {

    private final NotificationRequest request;
    private final NotificationDelivery delivery;
    private final NotificationAttempt attempt;
    private final NotificationTemplate template;
    private final NotificationTemplateContent content;
    private final NotificationRoute route;
    private final NotificationProvider provider;

    /** 只有待首次发送或明确失败等待重试可以创建新 Attempt。 */
    public boolean canSend() {
        return delivery.getStatus() == NotificationDeliveryStatus.PENDING
            || delivery.getStatus() == NotificationDeliveryStatus.RETRY_WAIT;
    }

    /** 确定终态不会因补偿重入再发送。 */
    public boolean isTerminal() {
        return delivery.getStatus() == NotificationDeliveryStatus.DELIVERED
            || delivery.getStatus() == NotificationDeliveryStatus.FAILED;
    }

    /** 当前配置不能追溯改变原发送查询，只约束新发送。 */
    public NotificationMessage render() {
        if (template == null || template.getStatus() != NotificationConfigurationStatus.ACTIVE
            || template.getMedium() != NotificationMedium.EMAIL || content == null
            || route == null || route.getStatus() != NotificationConfigurationStatus.ACTIVE
            || !route.getNotificationTemplateId().equals(request.getNotificationTemplateId())
            || provider == null || provider.getStatus() != NotificationConfigurationStatus.ACTIVE
            || provider.getMedium() != NotificationMedium.EMAIL) {
            throw new BizCheckFailException("通知当前模板、语言、路由或发送账号不可用");
        }
        return NotificationInput.render(content.getSubjectTemplate(), content.getBodyTemplate(),
            NotificationInput.merge(request.getDefaultParameters(), delivery.getParameters()));
    }
}

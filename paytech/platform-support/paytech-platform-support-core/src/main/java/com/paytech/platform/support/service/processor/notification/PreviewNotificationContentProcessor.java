package com.paytech.platform.support.service.processor.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.notificationmanagement.NotificationManagementPermissions;
import com.paytech.platform.support.delivery.domain.NotificationPreview;
import com.paytech.platform.support.delivery.service.NotificationManagementService;
import com.paytech.platform.support.service.processor.notification.request.PreviewNotificationContentRequest;

import lombok.AllArgsConstructor;

/**
 * 渲染内容草稿，供编辑页预览
 * <li>需要维护权限；不读写配置、不调用 Vault 或供应商</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class PreviewNotificationContentProcessor
    extends AbstractProcessor<PreviewNotificationContentRequest, NotificationPreview> {

    private final NotificationManagementSupport support;
    private final NotificationManagementService management;

    @Override
    protected NotificationPreview processRequest(PreviewNotificationContentRequest request) {
        return support.read(request.getActor(), request.getSession(), NotificationManagementPermissions.MANAGE,
            () -> management.preview(request.getSubjectTemplate(),
                request.getBodyTemplate(), request.getParameters()));
    }
}

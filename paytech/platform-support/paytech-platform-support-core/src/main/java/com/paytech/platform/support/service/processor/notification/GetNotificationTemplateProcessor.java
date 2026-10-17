package com.paytech.platform.support.service.processor.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.notificationmanagement.NotificationManagementPermissions;
import com.paytech.platform.support.delivery.domain.NotificationTemplateDetailView;
import com.paytech.platform.support.delivery.service.NotificationManagementService;
import com.paytech.platform.support.service.processor.notification.request.GetNotificationTemplateRequest;

import lombok.AllArgsConstructor;

/**
 * 读取通知模板、全部语言内容与路由
 * <li>权限重检与多表读取同一只读快照；模板不存在返回 404</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetNotificationTemplateProcessor
    extends AbstractProcessor<GetNotificationTemplateRequest, NotificationTemplateDetailView> {

    private final NotificationManagementSupport support;
    private final NotificationManagementService management;

    @Override
    protected NotificationTemplateDetailView processRequest(GetNotificationTemplateRequest request) {
        return support.read(request.getActor(), request.getSession(), NotificationManagementPermissions.READ,
            () -> management.template(request.getTemplateCode()));
    }
}

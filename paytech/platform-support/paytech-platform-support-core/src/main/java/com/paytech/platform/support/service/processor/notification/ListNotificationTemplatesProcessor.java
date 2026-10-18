package com.paytech.platform.support.service.processor.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.notificationmanagement.NotificationManagementPermissions;
import com.paytech.platform.support.delivery.domain.NotificationTemplateSummary;
import com.paytech.platform.support.delivery.service.NotificationManagementService;
import com.paytech.platform.support.service.processor.notification.request.ListNotificationTemplatesRequest;

import lombok.AllArgsConstructor;

/**
 * 按编码升序分页查询通知模板及其语言、路由状态
 * <li>权限重检与多表读取同一只读快照</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListNotificationTemplatesProcessor
    extends AbstractProcessor<ListNotificationTemplatesRequest, PagingResponseBody<NotificationTemplateSummary>> {

    private final NotificationManagementSupport support;
    private final NotificationManagementService management;

    @Override
    protected PagingResponseBody<NotificationTemplateSummary> processRequest(
        ListNotificationTemplatesRequest request) {
        return support.read(request.getActor(), request.getSession(), NotificationManagementPermissions.READ,
            () -> management.templates(request.getKeyword(), request.getStatus(), request.getPaging()));
    }
}

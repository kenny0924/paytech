package com.paytech.platform.support.service.processor.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.notificationmanagement.NotificationManagementPermissions;
import com.paytech.platform.support.delivery.domain.NotificationProviderView;
import com.paytech.platform.support.delivery.service.NotificationManagementService;
import com.paytech.platform.support.service.processor.notification.request.ListNotificationProvidersRequest;

import lombok.AllArgsConstructor;

/**
 * 按编码升序分页查询发送账号及其路由引用
 * <li>不返回发件地址、区域与凭据</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListNotificationProvidersProcessor
    extends AbstractProcessor<ListNotificationProvidersRequest, PagingResponseBody<NotificationProviderView>> {

    private final NotificationManagementSupport support;
    private final NotificationManagementService management;

    @Override
    protected PagingResponseBody<NotificationProviderView> processRequest(ListNotificationProvidersRequest request) {
        return support.read(request.getActor(), request.getSession(), NotificationManagementPermissions.READ,
            () -> management.providers(request.getPaging()));
    }
}

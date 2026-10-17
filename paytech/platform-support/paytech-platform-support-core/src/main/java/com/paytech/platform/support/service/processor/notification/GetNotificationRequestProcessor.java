package com.paytech.platform.support.service.processor.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.notificationmanagement.NotificationManagementPermissions;
import com.paytech.platform.support.delivery.domain.NotificationRequestDetailView;
import com.paytech.platform.support.delivery.service.NotificationRecordQueryService;
import com.paytech.platform.support.service.processor.notification.request.GetNotificationRequestRequest;

import lombok.AllArgsConstructor;

/**
 * 读取发送记录详情：请求、投递与尝试
 * <li>不返回收件邮箱、参数值与供应商流水号；不存在返回 404</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetNotificationRequestProcessor
    extends AbstractProcessor<GetNotificationRequestRequest, NotificationRequestDetailView> {

    private final NotificationManagementSupport support;
    private final NotificationRecordQueryService records;

    @Override
    protected NotificationRequestDetailView processRequest(GetNotificationRequestRequest request) {
        return support.read(request.getActor(), request.getSession(), NotificationManagementPermissions.READ,
            () -> records.request(request.getRequestId()));
    }
}

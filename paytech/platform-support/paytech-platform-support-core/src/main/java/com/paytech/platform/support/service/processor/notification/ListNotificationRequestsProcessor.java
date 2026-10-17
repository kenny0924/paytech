package com.paytech.platform.support.service.processor.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.notificationmanagement.NotificationManagementPermissions;
import com.paytech.platform.support.delivery.domain.NotificationRequestSummaryView;
import com.paytech.platform.support.delivery.service.NotificationRecordQueryService;
import com.paytech.platform.support.service.processor.notification.request.ListNotificationRequestsRequest;

import lombok.AllArgsConstructor;

/**
 * 按创建时间倒序分页查询普通通知发送记录
 * <li>不返回收件邮箱与参数值；认证邮件不留记录</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListNotificationRequestsProcessor
    extends AbstractProcessor<ListNotificationRequestsRequest, PagingResponseBody<NotificationRequestSummaryView>> {

    private final NotificationManagementSupport support;
    private final NotificationRecordQueryService records;

    @Override
    protected PagingResponseBody<NotificationRequestSummaryView> processRequest(ListNotificationRequestsRequest request) {
        return support.read(request.getActor(), request.getSession(), NotificationManagementPermissions.READ,
            () -> records.requests(request.getFilter(), request.getPaging()));
    }
}

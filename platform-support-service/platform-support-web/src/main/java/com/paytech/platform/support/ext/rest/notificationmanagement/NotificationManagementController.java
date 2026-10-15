package com.paytech.platform.support.ext.rest.notificationmanagement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.notificationmanagement.NotificationManagementApi;
import com.paytech.platform.support.api.notificationmanagement.request.ChangeNotificationStatusRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.CreateNotificationTemplateRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.PreviewNotificationContentRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.SaveNotificationContentRequestBody;
import com.paytech.platform.support.api.notificationmanagement.request.SaveNotificationRouteRequestBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationMutationResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationPreviewResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationProviderResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationRequestDetailResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationRequestSummaryResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationTemplateDetailResponseBody;
import com.paytech.platform.support.api.notificationmanagement.response.NotificationTemplateSummaryResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.service.processor.notification.ChangeNotificationProviderStatusProcessor;
import com.paytech.platform.support.service.processor.notification.ChangeNotificationRouteStatusProcessor;
import com.paytech.platform.support.service.processor.notification.ChangeNotificationTemplateStatusProcessor;
import com.paytech.platform.support.service.processor.notification.CreateNotificationTemplateProcessor;
import com.paytech.platform.support.service.processor.notification.GetNotificationRequestProcessor;
import com.paytech.platform.support.service.processor.notification.GetNotificationTemplateProcessor;
import com.paytech.platform.support.service.processor.notification.ListNotificationProvidersProcessor;
import com.paytech.platform.support.service.processor.notification.ListNotificationRequestsProcessor;
import com.paytech.platform.support.service.processor.notification.ListNotificationTemplatesProcessor;
import com.paytech.platform.support.service.processor.notification.PreviewNotificationContentProcessor;
import com.paytech.platform.support.service.processor.notification.SaveNotificationContentProcessor;
import com.paytech.platform.support.service.processor.notification.SaveNotificationRouteProcessor;

import lombok.AllArgsConstructor;

/** 通知管理的协议入口；鉴权、规则与事务委托处理器。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class NotificationManagementController implements NotificationManagementApi {

    private final NotificationManagementConverter converter;
    private final SaveNotificationRouteProcessor saveRoute;
    private final GetNotificationRequestProcessor getRequest;
    private final SaveNotificationContentProcessor saveContent;
    private final GetNotificationTemplateProcessor getTemplate;
    private final ListNotificationRequestsProcessor listRequests;
    private final ListNotificationProvidersProcessor listProviders;
    private final ListNotificationTemplatesProcessor listTemplates;
    private final PreviewNotificationContentProcessor previewContent;
    private final CreateNotificationTemplateProcessor createTemplate;
    private final ChangeNotificationRouteStatusProcessor changeRouteStatus;
    private final ChangeNotificationTemplateStatusProcessor changeTemplateStatus;
    private final ChangeNotificationProviderStatusProcessor changeProviderStatus;

    /** 模板分页查询。 */
    @Audit(action = "OPERATIONS_NOTIFICATION_TEMPLATES", resourceType = "NOTIFICATION_TEMPLATE",
        fallbackResourceId = "NOTIFICATION_TEMPLATE_CATALOG", recordSuccess = false)
    @Override
    public PagingResponseBody<NotificationTemplateSummaryResponseBody> templates(String actor, String session,
        Integer page, Integer size, String keyword, String status) {
        return listTemplates.process(converter.toTemplatesRequest(actor, session, page, size, keyword, status))
            .map(converter::toTemplateSummary);
    }

    /** 模板详情。 */
    @Audit(action = "OPERATIONS_NOTIFICATION_TEMPLATE", resourceType = "NOTIFICATION_TEMPLATE",
        fallbackResourceId = "NOTIFICATION_TEMPLATE_CATALOG", recordSuccess = false,
        resourceIdParameter = "templateCode")
    @Override
    public NotificationTemplateDetailResponseBody template(String actor, String session, String templateCode) {
        return converter.toTemplateDetail(getTemplate.process(converter.toTemplateRequest(actor, session,
            templateCode)));
    }

    /** 新建模板。 */
    @Audit(action = "OPERATIONS_NOTIFICATION_TEMPLATE_CREATE", resourceType = "NOTIFICATION_TEMPLATE",
        fallbackResourceId = "NOTIFICATION_TEMPLATE_CATALOG", recordSuccess = true)
    @Override
    public NotificationMutationResponseBody createTemplate(String actor, String session,
        CreateNotificationTemplateRequestBody request) {
        return converter.toMutation(createTemplate.process(converter.toCreateRequest(actor, session, request)));
    }

    /** 启停模板。 */
    @Audit(action = "OPERATIONS_NOTIFICATION_TEMPLATE_STATUS_CHANGE", resourceType = "NOTIFICATION_TEMPLATE",
        fallbackResourceId = "NOTIFICATION_TEMPLATE_CATALOG", recordSuccess = true,
        resourceIdParameter = "templateCode")
    @Override
    public NotificationMutationResponseBody changeTemplateStatus(String actor, String session, String templateCode,
        ChangeNotificationStatusRequestBody request) {
        return converter.toMutation(changeTemplateStatus.process(converter.toStatusRequest(actor, session,
            templateCode, request)));
    }

    /** 保存语言内容。 */
    @Audit(action = "OPERATIONS_NOTIFICATION_CONTENT_SAVE", resourceType = "NOTIFICATION_TEMPLATE",
        alternateResourceTypes = "NOTIFICATION_CONTENT",
        fallbackResourceId = "NOTIFICATION_TEMPLATE_CATALOG", recordSuccess = true,
        resourceIdParameter = "templateCode")
    @Override
    public NotificationMutationResponseBody saveContent(String actor, String session, String templateCode,
        String locale, SaveNotificationContentRequestBody request) {
        return converter.toMutation(saveContent.process(converter.toContentRequest(actor, session, templateCode,
            locale, request)));
    }

    /** 预览草稿，不记成功审计。 */
    @Audit(action = "OPERATIONS_NOTIFICATION_PREVIEW", resourceType = "NOTIFICATION_TEMPLATE",
        fallbackResourceId = "NOTIFICATION_TEMPLATE_CATALOG", recordSuccess = false)
    @Override
    public NotificationPreviewResponseBody preview(String actor, String session,
        PreviewNotificationContentRequestBody request) {
        return converter.toPreview(previewContent.process(converter.toPreviewRequest(actor, session, request)));
    }

    /** 保存路由。 */
    @Audit(action = "OPERATIONS_NOTIFICATION_ROUTE_SAVE", resourceType = "NOTIFICATION_TEMPLATE",
        alternateResourceTypes = "NOTIFICATION_ROUTE",
        fallbackResourceId = "NOTIFICATION_TEMPLATE_CATALOG", recordSuccess = true,
        resourceIdParameter = "templateCode")
    @Override
    public NotificationMutationResponseBody saveRoute(String actor, String session, String templateCode,
        SaveNotificationRouteRequestBody request) {
        return converter.toMutation(saveRoute.process(converter.toRouteRequest(actor, session, templateCode,
            request)));
    }

    /** 启停路由。 */
    @Audit(action = "OPERATIONS_NOTIFICATION_ROUTE_STATUS_CHANGE", resourceType = "NOTIFICATION_TEMPLATE",
        alternateResourceTypes = "NOTIFICATION_ROUTE",
        fallbackResourceId = "NOTIFICATION_TEMPLATE_CATALOG", recordSuccess = true,
        resourceIdParameter = "templateCode")
    @Override
    public NotificationMutationResponseBody changeRouteStatus(String actor, String session, String templateCode,
        ChangeNotificationStatusRequestBody request) {
        return converter.toMutation(changeRouteStatus.process(converter.toStatusRequest(actor, session,
            templateCode, request)));
    }

    /** 发送账号分页查询。 */
    @Audit(action = "OPERATIONS_NOTIFICATION_PROVIDERS", resourceType = "NOTIFICATION_PROVIDER",
        fallbackResourceId = "NOTIFICATION_PROVIDER_CATALOG", recordSuccess = false)
    @Override
    public PagingResponseBody<NotificationProviderResponseBody> providers(String actor, String session,
        Integer page, Integer size) {
        return listProviders.process(converter.toProvidersRequest(actor, session, page, size))
            .map(converter::toProvider);
    }

    /** 启停发送账号。 */
    @Audit(action = "OPERATIONS_NOTIFICATION_PROVIDER_STATUS_CHANGE", resourceType = "NOTIFICATION_PROVIDER",
        fallbackResourceId = "NOTIFICATION_PROVIDER_CATALOG", recordSuccess = true,
        resourceIdParameter = "providerCode")
    @Override
    public NotificationMutationResponseBody changeProviderStatus(String actor, String session, String providerCode,
        ChangeNotificationStatusRequestBody request) {
        return converter.toMutation(changeProviderStatus.process(converter.toStatusRequest(actor, session,
            providerCode, request)));
    }

    /** 发送记录分页查询。 */
    @Audit(action = "OPERATIONS_NOTIFICATION_REQUESTS", resourceType = "NOTIFICATION_REQUEST",
        fallbackResourceId = "NOTIFICATION_REQUEST_CATALOG", recordSuccess = false)
    @Override
    public PagingResponseBody<NotificationRequestSummaryResponseBody> requests(String actor, String session,
        Integer page, Integer size, String createdFrom, String createdTo, String sourceSystem, String sourceEventKey,
        String templateCode, String deliveryStatus) {
        return listRequests.process(converter.toRequestsRequest(actor, session, page, size, createdFrom, createdTo,
            sourceSystem, sourceEventKey, templateCode, deliveryStatus)).map(converter::toRequestSummary);
    }

    /** 发送记录详情。 */
    @Audit(action = "OPERATIONS_NOTIFICATION_REQUEST", resourceType = "NOTIFICATION_REQUEST",
        fallbackResourceId = "NOTIFICATION_REQUEST_CATALOG", recordSuccess = false,
        resourceIdParameter = "requestId")
    @Override
    public NotificationRequestDetailResponseBody request(String actor, String session, String requestId) {
        return converter.toRequestDetail(getRequest.process(converter.toRequestDetailRequest(actor, session,
            requestId)));
    }
}

package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.ChangeNotificationStatusRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.CreateNotificationProviderRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.CreateNotificationTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationPreviewResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationProviderSummary;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationProviderTestSendResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationRequestDetailResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationRequestSummary;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationTemplateDetailResponseBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.NotificationTemplateSummary;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.PreviewNotificationContentRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.ReplaceNotificationCredentialRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.SaveNotificationContentRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.SaveNotificationRouteRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.TestSendNotificationProviderRequestBody;
import com.paytech.operations.console.bff.service.model.NotificationManagementModels.UpdateNotificationProviderRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/** Platform 通知管理的本地端口；鉴权、规则与审计归 Platform。 */
public interface PlatformNotificationManagementClient {

    /** 模板分页查询。 */
    PagingResponseBody<NotificationTemplateSummary> templates(OperatorContext context, Integer page, Integer size,
        String keyword, String status);

    /** 模板详情。 */
    NotificationTemplateDetailResponseBody template(OperatorContext context, String templateCode);

    /** 新建模板。 */
    NotificationMutationResponseBody createTemplate(OperatorContext context,
        CreateNotificationTemplateRequestBody request);

    /** 启停模板。 */
    NotificationMutationResponseBody changeTemplateStatus(OperatorContext context, String templateCode,
        ChangeNotificationStatusRequestBody request);

    /** 保存语言内容。 */
    NotificationMutationResponseBody saveContent(OperatorContext context, String templateCode, String locale,
        SaveNotificationContentRequestBody request);

    /** 预览内容草稿。 */
    NotificationPreviewResponseBody preview(OperatorContext context, PreviewNotificationContentRequestBody request);

    /** 保存路由。 */
    NotificationMutationResponseBody saveRoute(OperatorContext context, String templateCode,
        SaveNotificationRouteRequestBody request);

    /** 启停路由。 */
    NotificationMutationResponseBody changeRouteStatus(OperatorContext context, String templateCode,
        ChangeNotificationStatusRequestBody request);

    /** 发送账号分页查询。 */
    PagingResponseBody<NotificationProviderSummary> providers(OperatorContext context, Integer page, Integer size);

    /** 启停发送账号。 */
    NotificationMutationResponseBody changeProviderStatus(OperatorContext context, String providerCode,
        ChangeNotificationStatusRequestBody request);

    /** 新建发送账号。 */
    NotificationMutationResponseBody createProvider(OperatorContext context,
        CreateNotificationProviderRequestBody request);

    /** 修改发送账号。 */
    NotificationMutationResponseBody updateProvider(OperatorContext context, String providerCode,
        UpdateNotificationProviderRequestBody request);

    /** 录入或更换发送账号密钥。 */
    NotificationMutationResponseBody replaceProviderCredential(OperatorContext context, String providerCode,
        ReplaceNotificationCredentialRequestBody request);

    /** 发送账号测试发送。 */
    NotificationProviderTestSendResponseBody testSendProvider(OperatorContext context, String providerCode,
        TestSendNotificationProviderRequestBody request);

    /** 发送记录分页查询。 */
    PagingResponseBody<NotificationRequestSummary> requests(OperatorContext context, Integer page, Integer size,
        String createdFrom, String createdTo, String sourceSystem, String sourceEventKey, String templateCode,
        String deliveryStatus);

    /** 发送记录详情。 */
    NotificationRequestDetailResponseBody request(OperatorContext context, String requestId);
}

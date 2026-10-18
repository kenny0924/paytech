package com.paytech.operations.console.bff.integration;

import java.util.List;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.ChangeWebhookEndpointStatusRequestBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.CreateWebhookEndpointResponseBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.ReplayWebhookDeliveryRequestBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.ReplayWebhookDeliveryResponseBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.RotateWebhookSecretRequestBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.RotateWebhookSecretResponseBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.SaveWebhookEndpointRequestBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.WebhookClientSummary;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.WebhookDeliveryDetailResponseBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.WebhookDeliverySummary;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.WebhookEndpointMutationResponseBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.WebhookEndpointResponseBody;
import com.paytech.operations.console.bff.service.model.WebhookManagementModels.WebhookTestResponseBody;

/** Platform Webhook 管理的本地端口；鉴权、规则、密钥与审计归 Platform。 */
public interface PlatformWebhookManagementClient {

    /** Client 搜索。 */
    PagingResponseBody<WebhookClientSummary> clients(OperatorContext context, String keyword, Integer page,
        Integer size);

    /** Client 的全部端点。 */
    List<WebhookEndpointResponseBody> endpoints(OperatorContext context, String clientId);

    /** 新建端点并生成首个签名密钥。 */
    CreateWebhookEndpointResponseBody createEndpoint(OperatorContext context, String clientId,
        SaveWebhookEndpointRequestBody request);

    /** 修改端点地址与订阅事件类型。 */
    WebhookEndpointMutationResponseBody updateEndpoint(OperatorContext context, String endpointId,
        SaveWebhookEndpointRequestBody request);

    /** 启用或停用端点。 */
    WebhookEndpointMutationResponseBody changeEndpointStatus(OperatorContext context, String endpointId,
        ChangeWebhookEndpointStatusRequestBody request);

    /** 轮换签名密钥。 */
    RotateWebhookSecretResponseBody rotateSecret(OperatorContext context, String endpointId,
        RotateWebhookSecretRequestBody request);

    /** 发送测试事件。 */
    WebhookTestResponseBody testEndpoint(OperatorContext context, String endpointId);

    /** 投递分页查询。 */
    PagingResponseBody<WebhookDeliverySummary> deliveries(OperatorContext context, String clientId,
        String createdFrom, String createdTo, Integer page, Integer size, String endpointId, String eventType,
        String status, String eventId);

    /** 投递详情。 */
    WebhookDeliveryDetailResponseBody delivery(OperatorContext context, String deliveryId);

    /** Replay 已结束的投递。 */
    ReplayWebhookDeliveryResponseBody replayDelivery(OperatorContext context, String deliveryId,
        ReplayWebhookDeliveryRequestBody request);
}

package com.paytech.platform.support.ext.rest.webhookmanagement;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.webhookmanagement.WebhookManagementApi;
import com.paytech.platform.support.api.webhookmanagement.request.ChangeWebhookEndpointStatusRequestBody;
import com.paytech.platform.support.api.webhookmanagement.request.ReplayWebhookDeliveryRequestBody;
import com.paytech.platform.support.api.webhookmanagement.request.RotateWebhookSecretRequestBody;
import com.paytech.platform.support.api.webhookmanagement.request.SaveWebhookEndpointRequestBody;
import com.paytech.platform.support.api.webhookmanagement.response.CreateWebhookEndpointResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.ReplayWebhookDeliveryResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.RotateWebhookSecretResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookClientResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookDeliveryDetailResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookDeliverySummaryResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookEndpointMutationResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookEndpointResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookTestResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.service.processor.webhook.ChangeWebhookEndpointStatusProcessor;
import com.paytech.platform.support.service.processor.webhook.CreateWebhookEndpointProcessor;
import com.paytech.platform.support.service.processor.webhook.GetWebhookDeliveryProcessor;
import com.paytech.platform.support.service.processor.webhook.ListWebhookClientsProcessor;
import com.paytech.platform.support.service.processor.webhook.ListWebhookDeliveriesProcessor;
import com.paytech.platform.support.service.processor.webhook.ListWebhookEndpointsProcessor;
import com.paytech.platform.support.service.processor.webhook.ReplayWebhookDeliveryProcessor;
import com.paytech.platform.support.service.processor.webhook.RotateWebhookSecretProcessor;
import com.paytech.platform.support.service.processor.webhook.TestWebhookEndpointProcessor;
import com.paytech.platform.support.service.processor.webhook.UpdateWebhookEndpointProcessor;

import lombok.AllArgsConstructor;

/** Webhook 管理的协议入口；鉴权、规则与事务委托处理器；含密钥明文的响应不缓存。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = {"operations.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class WebhookManagementController implements WebhookManagementApi {

    private final WebhookManagementConverter converter;
    private final ListWebhookClientsProcessor listClients;
    private final TestWebhookEndpointProcessor testEndpoint;
    private final RotateWebhookSecretProcessor rotateSecret;
    private final GetWebhookDeliveryProcessor getDelivery;
    private final ListWebhookEndpointsProcessor listEndpoints;
    private final ReplayWebhookDeliveryProcessor replayDelivery;
    private final CreateWebhookEndpointProcessor createEndpoint;
    private final UpdateWebhookEndpointProcessor updateEndpoint;
    private final ListWebhookDeliveriesProcessor listDeliveries;
    private final ChangeWebhookEndpointStatusProcessor changeStatus;

    /** Client 搜索。 */
    @Audit(action = "OPERATIONS_WEBHOOK_CLIENTS", resourceType = "WEBHOOK_CLIENT", fallbackResourceId = "WEBHOOK_CATALOG",
        recordSuccess = false)
    @Override
    public PagingResponseBody<WebhookClientResponseBody> clients(String actor, String session, String keyword,
        Integer page, Integer size) {
        return listClients.process(converter.toClientsRequest(actor, session, keyword, page, size))
            .map(converter::toClient);
    }

    /** 端点列表。 */
    @Audit(action = "OPERATIONS_WEBHOOK_ENDPOINTS", resourceType = "WEBHOOK_CLIENT", resourceIdParameter = "clientId",
        fallbackResourceId = "WEBHOOK_CATALOG", recordSuccess = false)
    @Override
    public List<WebhookEndpointResponseBody> endpoints(String actor, String session, String clientId) {
        return listEndpoints.process(converter.toEndpointsRequest(actor, session, clientId)).stream()
            .map(converter::toEndpoint).toList();
    }

    /** 新建端点；201 且不缓存。 */
    @Audit(action = "OPERATIONS_WEBHOOK_ENDPOINT_CREATE", resourceType = "WEBHOOK_CLIENT", resourceIdParameter = "clientId",
        alternateResourceTypes = "WEBHOOK_ENDPOINT", fallbackResourceId = "WEBHOOK_CATALOG", recordSuccess = true)
    @Override
    public ResponseEntity<CreateWebhookEndpointResponseBody> createEndpoint(String actor, String session,
        String clientId, SaveWebhookEndpointRequestBody request) {
        var created = createEndpoint.process(converter.toCreateRequest(actor, session, clientId, request));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(converter.toCreated(created));
    }

    /** 修改端点。 */
    @Audit(action = "OPERATIONS_WEBHOOK_ENDPOINT_UPDATE", resourceType = "WEBHOOK_ENDPOINT", resourceIdParameter = "endpointId",
        fallbackResourceId = "WEBHOOK_CATALOG", recordSuccess = true)
    @Override
    public WebhookEndpointMutationResponseBody updateEndpoint(String actor, String session, String endpointId,
        SaveWebhookEndpointRequestBody request) {
        return converter.toMutation(updateEndpoint.process(converter.toUpdateRequest(actor, session, endpointId,
            request)));
    }

    /** 启停端点。 */
    @Audit(action = "OPERATIONS_WEBHOOK_ENDPOINT_STATUS_CHANGE", resourceType = "WEBHOOK_ENDPOINT",
        resourceIdParameter = "endpointId", fallbackResourceId = "WEBHOOK_CATALOG", recordSuccess = true)
    @Override
    public WebhookEndpointMutationResponseBody changeEndpointStatus(String actor, String session, String endpointId,
        ChangeWebhookEndpointStatusRequestBody request) {
        return converter.toMutation(changeStatus.process(converter.toStatusRequest(actor, session, endpointId,
            request)));
    }

    /** 轮换密钥；不缓存。 */
    @Audit(action = "OPERATIONS_WEBHOOK_SECRET_ROTATE", resourceType = "WEBHOOK_ENDPOINT", resourceIdParameter = "endpointId",
        fallbackResourceId = "WEBHOOK_CATALOG", recordSuccess = true)
    @Override
    public ResponseEntity<RotateWebhookSecretResponseBody> rotateSecret(String actor, String session,
        String endpointId, RotateWebhookSecretRequestBody request) {
        var rotated = rotateSecret.process(converter.toRotateRequest(actor, session, endpointId, request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(converter.toRotated(rotated));
    }

    /** 发送测试事件。 */
    @Audit(action = "OPERATIONS_WEBHOOK_ENDPOINT_TEST", resourceType = "WEBHOOK_ENDPOINT", resourceIdParameter = "endpointId",
        fallbackResourceId = "WEBHOOK_CATALOG", recordSuccess = true)
    @Override
    public WebhookTestResponseBody testEndpoint(String actor, String session, String endpointId) {
        return converter.toTest(testEndpoint.process(converter.toTestRequest(actor, session, endpointId)));
    }

    /** 投递分页。 */
    @Audit(action = "OPERATIONS_WEBHOOK_DELIVERIES", resourceType = "WEBHOOK_CLIENT", resourceIdParameter = "clientId",
        fallbackResourceId = "WEBHOOK_CATALOG", recordSuccess = false)
    @Override
    public PagingResponseBody<WebhookDeliverySummaryResponseBody> deliveries(String actor, String session,
        String clientId, String createdFrom, String createdTo, Integer page, Integer size, String endpointId,
        String eventType, String status, String eventId) {
        return listDeliveries.process(converter.toDeliveriesRequest(actor, session, clientId, createdFrom, createdTo,
            page, size, endpointId, eventType, status, eventId)).map(converter::toSummary);
    }

    /** 投递详情。 */
    @Audit(action = "OPERATIONS_WEBHOOK_DELIVERY", resourceType = "WEBHOOK_DELIVERY",
        resourceIdParameter = "deliveryId", fallbackResourceId = "WEBHOOK_CATALOG", recordSuccess = false)
    @Override
    public WebhookDeliveryDetailResponseBody delivery(String actor, String session, String deliveryId) {
        return converter.toDetail(getDelivery.process(converter.toDeliveryRequest(actor, session, deliveryId)));
    }

    /** Replay；201。 */
    @Audit(action = "OPERATIONS_WEBHOOK_DELIVERY_REPLAY", resourceType = "WEBHOOK_DELIVERY", resourceIdParameter = "deliveryId",
        fallbackResourceId = "WEBHOOK_CATALOG", recordSuccess = true)
    @Override
    public ResponseEntity<ReplayWebhookDeliveryResponseBody> replayDelivery(String actor, String session,
        String deliveryId, ReplayWebhookDeliveryRequestBody request) {
        long replayId = replayDelivery.process(converter.toReplayRequest(actor, session, deliveryId, request));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new ReplayWebhookDeliveryResponseBody(Long.toString(replayId)));
    }
}

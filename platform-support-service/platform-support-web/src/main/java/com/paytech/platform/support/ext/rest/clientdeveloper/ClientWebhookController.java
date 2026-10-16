package com.paytech.platform.support.ext.rest.clientdeveloper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs;
import com.paytech.platform.support.api.clientwebhook.ClientWebhookApi;
import com.paytech.platform.support.api.clientwebhook.request.ClientChangeWebhookEndpointStatusRequestBody;
import com.paytech.platform.support.api.clientwebhook.request.ClientRotateWebhookSecretRequestBody;
import com.paytech.platform.support.api.clientwebhook.request.ClientCreateWebhookEndpointRequestBody;
import com.paytech.platform.support.api.clientwebhook.request.ClientSaveWebhookEndpointRequestBody;
import com.paytech.platform.support.api.clientwebhook.response.ClientWebhookDeliveryDetailResponseBody;
import com.paytech.platform.support.api.clientwebhook.response.ClientWebhookEndpointsResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.CreateWebhookEndpointResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.ReplayWebhookDeliveryResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.RotateWebhookSecretResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookDeliverySummaryResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookEndpointMutationResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookTestResponseBody;
import com.paytech.platform.support.delivery.core.enums.WebhookDeliveryStatus;
import com.paytech.platform.support.delivery.core.enums.WebhookEndpointStatus;
import com.paytech.platform.support.delivery.domain.WebhookEventTypes;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.ext.rest.webhookmanagement.WebhookManagementConverter;
import com.paytech.platform.support.service.processor.webhook.ClientCreateWebhookEndpointProcessor;
import com.paytech.platform.support.service.processor.webhook.ClientReplayWebhookDeliveryProcessor;
import com.paytech.platform.support.service.processor.webhook.ClientRotateWebhookSecretProcessor;
import com.paytech.platform.support.service.processor.webhook.ClientTestWebhookEndpointProcessor;
import com.paytech.platform.support.service.processor.webhook.ClientUpdateWebhookEndpointProcessor;
import com.paytech.platform.support.service.processor.webhook.ClientWebhookDeliveriesProcessor;
import com.paytech.platform.support.service.processor.webhook.ClientWebhookDeliveryProcessor;
import com.paytech.platform.support.service.processor.webhook.ClientWebhookEndpointsProcessor;
import com.paytech.platform.support.service.processor.webhook.request.ClientWebhookRequest;

import lombok.AllArgsConstructor;

/** 私网工作区自助 Webhook 入口；投影沿用运营转换器，详情改用客户可见投影；一次性密钥响应不缓存。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = {"client.authentication.enabled", "webhook.enabled"}, havingValue = "true")
public class ClientWebhookController implements ClientWebhookApi {
    private final ClientDeveloperConverter converter;
    private final WebhookManagementConverter webhookConverter;
    private final ClientWebhookEndpointsProcessor endpointsProcessor;
    private final ClientCreateWebhookEndpointProcessor createProcessor;
    private final ClientUpdateWebhookEndpointProcessor updateProcessor;
    private final ClientRotateWebhookSecretProcessor rotateProcessor;
    private final ClientTestWebhookEndpointProcessor testProcessor;
    private final ClientWebhookDeliveriesProcessor deliveriesProcessor;
    private final ClientWebhookDeliveryProcessor deliveryProcessor;
    private final ClientReplayWebhookDeliveryProcessor replayProcessor;

    /** 端点列表。 */
    @Override
    public ClientWebhookEndpointsResponseBody endpoints(String accountId, String sessionId) {
        return new ClientWebhookEndpointsResponseBody(endpointsProcessor.process(base(accountId, sessionId).build())
            .stream().map(webhookConverter::toEndpoint).toList());
    }

    /** 新建端点；201 且不缓存。 */
    @Override
    @Audit(action = "CLIENT_WEBHOOK_ENDPOINT_CREATE", resourceType = "WEBHOOK_ENDPOINT", fallbackResourceId = "NEW")
    public ResponseEntity<CreateWebhookEndpointResponseBody> createEndpoint(String accountId, String sessionId,
        ClientCreateWebhookEndpointRequestBody request) {
        var created = createProcessor.process(base(accountId, sessionId).url(request.getUrl())
            .eventTypes(WebhookEventTypes.validate(request.getEventTypes())).build());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(webhookConverter.toCreated(created));
    }

    /** 修改地址与订阅。 */
    @Override
    @Audit(action = "CLIENT_WEBHOOK_ENDPOINT_UPDATE", resourceType = "WEBHOOK_ENDPOINT", resourceIdParameter = "endpointId")
    public WebhookEndpointMutationResponseBody updateEndpoint(String accountId, String sessionId, String endpointId,
        ClientSaveWebhookEndpointRequestBody request) {
        return webhookConverter.toMutation(updateProcessor.process(base(accountId, sessionId)
            .endpointId(converter.pathId(endpointId)).url(request.getUrl())
            .eventTypes(WebhookEventTypes.validate(request.getEventTypes())).expectedVersion(request.getExpectedVersion())
            .build()));
    }

    /** 启用或停用。 */
    @Override
    @Audit(action = "CLIENT_WEBHOOK_ENDPOINT_STATUS_CHANGE", resourceType = "WEBHOOK_ENDPOINT",
        resourceIdParameter = "endpointId")
    public WebhookEndpointMutationResponseBody changeEndpointStatus(String accountId, String sessionId,
        String endpointId, ClientChangeWebhookEndpointStatusRequestBody request) {
        return webhookConverter.toMutation(updateProcessor.process(base(accountId, sessionId)
            .endpointId(converter.pathId(endpointId)).status(endpointStatus(request.getStatus()))
            .expectedVersion(request.getExpectedVersion()).build()));
    }

    /** 轮换密钥；201 且不缓存。 */
    @Override
    @Audit(action = "CLIENT_WEBHOOK_SECRET_ROTATE", resourceType = "WEBHOOK_ENDPOINT", resourceIdParameter = "endpointId")
    public ResponseEntity<RotateWebhookSecretResponseBody> rotateSecret(String accountId, String sessionId,
        String endpointId, ClientRotateWebhookSecretRequestBody request) {
        var rotated = rotateProcessor.process(base(accountId, sessionId).endpointId(converter.pathId(endpointId))
            .immediate(request.isImmediate()).build());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(webhookConverter.toRotated(rotated));
    }

    /** 发送测试事件。 */
    @Override
    @Audit(action = "CLIENT_WEBHOOK_ENDPOINT_TEST", resourceType = "WEBHOOK_ENDPOINT", resourceIdParameter = "endpointId")
    public WebhookTestResponseBody testEndpoint(String accountId, String sessionId, String endpointId) {
        return webhookConverter.toTest(testProcessor.process(base(accountId, sessionId)
            .endpointId(converter.pathId(endpointId)).build()));
    }

    /** 投递分页；时间范围默认最近 30 天。 */
    @Override
    public PagingResponseBody<WebhookDeliverySummaryResponseBody> deliveries(String accountId, String sessionId,
        String createdFrom, String createdTo, Integer page, Integer size, String endpointId, String eventType,
        String status) {
        var range = converter.deliveryRange(createdFrom, createdTo, java.time.Instant.now());
        if (eventType != null && eventType.length() > 64) {
            throw new com.paytech.foundation.common.error.InvalidParameterException("eventType 超长");
        }
        return deliveriesProcessor.process(base(accountId, sessionId).createdFrom(range[0]).createdTo(range[1])
            .endpointId(converter.optionalId(endpointId)).eventType(eventType)
            .deliveryStatus(status == null ? null : deliveryStatus(status))
            .paging(ClientApiManagementInputs.paging(page, size)).build()).map(webhookConverter::toSummary);
    }

    /** 投递详情。 */
    @Override
    public ClientWebhookDeliveryDetailResponseBody delivery(String accountId, String sessionId, String deliveryId) {
        return converter.toDeliveryDetail(deliveryProcessor.process(base(accountId, sessionId)
            .deliveryId(converter.pathId(deliveryId)).build()));
    }

    /** 重发；201 返回新投递 ID。 */
    @Override
    @Audit(action = "CLIENT_WEBHOOK_DELIVERY_REPLAY", resourceType = "WEBHOOK_DELIVERY", resourceIdParameter = "deliveryId")
    public ResponseEntity<ReplayWebhookDeliveryResponseBody> replayDelivery(String accountId, String sessionId,
        String deliveryId) {
        long replayId = replayProcessor.process(base(accountId, sessionId).deliveryId(converter.pathId(deliveryId))
            .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ReplayWebhookDeliveryResponseBody(Long.toString(replayId)));
    }

    /** 可信会话坐标。 */
    ClientWebhookRequest.ClientWebhookRequestBuilder base(String accountId, String sessionId) {
        return ClientWebhookRequest.builder().accountId(converter.session(accountId)).sessionId(converter.session(sessionId));
    }

    /** 闭合状态解析，未知值返回 400。 */
    static WebhookEndpointStatus endpointStatus(String value) {
        for (var status : WebhookEndpointStatus.values()) {
            if (status.getCode().equals(value)) {
                return status;
            }
        }
        throw new com.paytech.foundation.common.error.InvalidParameterException("status 不是支持的值");
    }

    /** 闭合状态解析，未知值返回 400。 */
    static WebhookDeliveryStatus deliveryStatus(String value) {
        for (var status : WebhookDeliveryStatus.values()) {
            if (status.getCode().equals(value)) {
                return status;
            }
        }
        throw new com.paytech.foundation.common.error.InvalidParameterException("status 不是支持的值");
    }
}

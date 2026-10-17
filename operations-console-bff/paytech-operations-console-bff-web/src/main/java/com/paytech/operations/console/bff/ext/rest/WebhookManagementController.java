package com.paytech.operations.console.bff.ext.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformWebhookManagementClient;
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

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Webhook 管理的浏览器入口，全部简单转发；沿用公共 Origin、PLT-I02 与可信身份边界
 * <li>新建与轮换响应含密钥明文，依赖全局 no-store 与 /webhook-admin 元数据日志</li>
 */
@RestController
@Validated
@AllArgsConstructor
@RequestMapping("/webhook-admin/v1")
public class WebhookManagementController {

    private final PlatformWebhookManagementClient client;

    /** Client 搜索。 */
    @GetMapping("/clients")
    public PagingResponseBody<WebhookClientSummary> clients(OperatorContext context,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.clients(context, keyword, page, size);
    }

    /** Client 的全部端点。 */
    @GetMapping("/clients/{clientId}/endpoints")
    public List<WebhookEndpointResponseBody> endpoints(OperatorContext context,
        @PathVariable("clientId") String clientId) {
        return client.endpoints(context, clientId);
    }

    /** 新建端点并生成首个签名密钥。 */
    @PostMapping("/clients/{clientId}/endpoints")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateWebhookEndpointResponseBody createEndpoint(OperatorContext context,
        @PathVariable("clientId") String clientId, @Valid @RequestBody SaveWebhookEndpointRequestBody request) {
        return client.createEndpoint(context, clientId, request);
    }

    /** 修改端点地址与订阅事件类型。 */
    @PutMapping("/endpoints/{endpointId}")
    public WebhookEndpointMutationResponseBody updateEndpoint(OperatorContext context,
        @PathVariable("endpointId") String endpointId, @Valid @RequestBody SaveWebhookEndpointRequestBody request) {
        return client.updateEndpoint(context, endpointId, request);
    }

    /** 启用或停用端点。 */
    @PatchMapping("/endpoints/{endpointId}/status")
    public WebhookEndpointMutationResponseBody changeEndpointStatus(OperatorContext context,
        @PathVariable("endpointId") String endpointId,
        @Valid @RequestBody ChangeWebhookEndpointStatusRequestBody request) {
        return client.changeEndpointStatus(context, endpointId, request);
    }

    /** 轮换签名密钥。 */
    @PostMapping("/endpoints/{endpointId}/signing-secret/rotations")
    public RotateWebhookSecretResponseBody rotateSecret(OperatorContext context,
        @PathVariable("endpointId") String endpointId, @Valid @RequestBody RotateWebhookSecretRequestBody request) {
        return client.rotateSecret(context, endpointId, request);
    }

    /** 发送测试事件；无请求体，携带 Body 时被请求边界拒绝。 */
    @PostMapping("/endpoints/{endpointId}/tests")
    public WebhookTestResponseBody testEndpoint(OperatorContext context,
        @PathVariable("endpointId") String endpointId) {
        return client.testEndpoint(context, endpointId);
    }

    /** 投递分页查询。 */
    @GetMapping("/deliveries")
    public PagingResponseBody<WebhookDeliverySummary> deliveries(OperatorContext context,
        @RequestParam("clientId") String clientId,
        @RequestParam("createdFrom") String createdFrom,
        @RequestParam("createdTo") String createdTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "endpointId", required = false) String endpointId,
        @RequestParam(value = "eventType", required = false) String eventType,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "eventId", required = false) String eventId) {
        return client.deliveries(context, clientId, createdFrom, createdTo, page, size, endpointId, eventType, status,
            eventId);
    }

    /** 投递详情。 */
    @GetMapping("/deliveries/{deliveryId}")
    public WebhookDeliveryDetailResponseBody delivery(OperatorContext context,
        @PathVariable("deliveryId") String deliveryId) {
        return client.delivery(context, deliveryId);
    }

    /** Replay 已结束的投递。 */
    @PostMapping("/deliveries/{deliveryId}/replays")
    @ResponseStatus(HttpStatus.CREATED)
    public ReplayWebhookDeliveryResponseBody replayDelivery(OperatorContext context,
        @PathVariable("deliveryId") String deliveryId, @Valid @RequestBody ReplayWebhookDeliveryRequestBody request) {
        return client.replayDelivery(context, deliveryId, request);
    }
}

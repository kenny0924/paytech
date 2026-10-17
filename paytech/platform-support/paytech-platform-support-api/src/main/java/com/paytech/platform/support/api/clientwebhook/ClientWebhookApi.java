package com.paytech.platform.support.api.clientwebhook;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.clientwebhook.request.ClientChangeWebhookEndpointStatusRequestBody;
import com.paytech.platform.support.api.clientwebhook.request.ClientCreateWebhookEndpointRequestBody;
import com.paytech.platform.support.api.clientwebhook.request.ClientRotateWebhookSecretRequestBody;
import com.paytech.platform.support.api.clientwebhook.request.ClientSaveWebhookEndpointRequestBody;
import com.paytech.platform.support.api.clientwebhook.response.ClientWebhookDeliveryDetailResponseBody;
import com.paytech.platform.support.api.clientwebhook.response.ClientWebhookEndpointsResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.CreateWebhookEndpointResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.ReplayWebhookDeliveryResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.RotateWebhookSecretResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookDeliverySummaryResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookEndpointMutationResponseBody;
import com.paytech.platform.support.api.webhookmanagement.response.WebhookTestResponseBody;

import jakarta.validation.Valid;

/**
 * 工作区成员自助管理本工作区的 Webhook；只供 Client BFF 私网调用
 * <li>租户只取自当前有效的 CLIENT 租户上下文会话；按端点或投递 ID 的操作不属于当前工作区时返回 404。</li>
 * <li>读取需要 CLIENT_WEBHOOK_READ，写入需要 CLIENT_WEBHOOK_MANAGE；无权限返回 403 CLIENT_PERMISSION_DENIED。</li>
 * <li>响应不含运营账号、运营备注、内部系统名与内部事件主键；签名密钥明文只在新建与轮换响应中出现一次。</li>
 */
@HttpExchange("/client-webhooks/v1")
public interface ClientWebhookApi {

    /** 本工作区全部端点与当前签名密钥概况。 */
    @GetExchange("/endpoints")
    ClientWebhookEndpointsResponseBody endpoints(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId);

    /** 新建端点（每个客户最多 5 个，含已停用）；201 返回一次签名密钥明文。 */
    @PostExchange("/endpoints")
    ResponseEntity<CreateWebhookEndpointResponseBody> createEndpoint(
        @RequestHeader("X-Gateway-Actor-Subject") String accountId, @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody ClientCreateWebhookEndpointRequestBody request);

    /** 修改地址与订阅；必须携带当前版本。 */
    @PutExchange("/endpoints/{endpointId}")
    WebhookEndpointMutationResponseBody updateEndpoint(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("endpointId") String endpointId,
        @Valid @RequestBody ClientSaveWebhookEndpointRequestBody request);

    /** 启用或停用。 */
    @PostExchange("/endpoints/{endpointId}/status")
    WebhookEndpointMutationResponseBody changeEndpointStatus(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("endpointId") String endpointId,
        @Valid @RequestBody ClientChangeWebhookEndpointStatusRequestBody request);

    /** 轮换签名密钥；201 返回一次新密钥明文。 */
    @PostExchange("/endpoints/{endpointId}/secret-rotation")
    ResponseEntity<RotateWebhookSecretResponseBody> rotateSecret(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("endpointId") String endpointId,
        @Valid @RequestBody ClientRotateWebhookSecretRequestBody request);

    /** 向端点同步发送一次 webhook.ping 测试事件，不写投递记录。 */
    @PostExchange("/endpoints/{endpointId}/test-events")
    WebhookTestResponseBody testEndpoint(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("endpointId") String endpointId);

    /**
     * 本工作区投递分页
     * <li>createdFrom/createdTo 为 UTC ISO-8601，可省略，默认最近 30 天；跨度不超过 31 天。</li>
     */
    @GetExchange("/deliveries")
    PagingResponseBody<WebhookDeliverySummaryResponseBody> deliveries(
        @RequestHeader("X-Gateway-Actor-Subject") String accountId, @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "createdFrom", required = false) String createdFrom,
        @RequestParam(value = "createdTo", required = false) String createdTo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "endpointId", required = false) String endpointId,
        @RequestParam(value = "eventType", required = false) String eventType,
        @RequestParam(value = "status", required = false) String status);

    /** 投递详情与尝试记录。 */
    @GetExchange("/deliveries/{deliveryId}")
    ClientWebhookDeliveryDetailResponseBody delivery(@RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("deliveryId") String deliveryId);

    /** 重发：生成新投递，事件 ID 与内容不变；201 返回新投递 ID。 */
    @PostExchange("/deliveries/{deliveryId}/replay")
    ResponseEntity<ReplayWebhookDeliveryResponseBody> replayDelivery(
        @RequestHeader("X-Gateway-Actor-Subject") String accountId, @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("deliveryId") String deliveryId);
}

package com.paytech.client.console.bff.ext.rest;

import com.paytech.client.console.bff.service.processor.ClientSessionProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.clientapicredential.ClientApiCredentialApi;
import com.paytech.platform.support.api.clientapicredential.request.CreateClientApiCredentialRequestBody;
import com.paytech.platform.support.api.clientapicredential.request.RevokeClientApiCredentialRequestBody;
import com.paytech.platform.support.api.clientapicredential.request.RotateClientApiCredentialRequestBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiCredentialResponseBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiCredentialSecretResponseBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiCredentialsResponseBody;
import com.paytech.platform.support.api.clientapicredential.response.ClientApiScopesResponseBody;
import com.paytech.platform.support.api.clientwebhook.ClientWebhookApi;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作区“开发者”接口的薄转发：API 凭证与 Webhook 自助
 * <li>租户上下文与权限由 {@code ClientDeveloperWebConfiguration} 先行判断；租户由 Platform 从会话解析。</li>
 * <li>只透传 Platform 合同与状态码，不缓存、不记录一次性密钥。</li>
 */
@RestController
@lombok.AllArgsConstructor
@RequestMapping("/client-developer/v1")
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ClientDeveloperController {

    private final ClientApiCredentialApi credentials;
    private final ClientWebhookApi webhooks;
    private final ClientSessionProcessor sessions;

    /** D01 凭证列表。 */
    @GetMapping("/credentials")
    public ClientApiCredentialsResponseBody credentials(HttpServletRequest request,
        @RequestParam(value = "status", required = false) String status) {
        var id = sessions.identity(request);
        return credentials.credentials(id[0], id[1], status);
    }

    /** D02 凭证详情。 */
    @GetMapping("/credentials/{credentialId}")
    public ClientApiCredentialResponseBody credential(HttpServletRequest request, @PathVariable String credentialId) {
        var id = sessions.identity(request);
        return credentials.credential(id[0], id[1], credentialId);
    }

    /** D03 可选 Scope 目录。 */
    @GetMapping("/scopes")
    public ClientApiScopesResponseBody scopes(HttpServletRequest request) {
        var id = sessions.identity(request);
        return credentials.scopes(id[0], id[1]);
    }

    /** D04 创建凭证；密钥只在本次响应出现。 */
    @PostMapping("/credentials")
    public ResponseEntity<ClientApiCredentialSecretResponseBody> create(HttpServletRequest request,
        @Valid @RequestBody CreateClientApiCredentialRequestBody body) {
        var id = sessions.identity(request);
        return relay(credentials.create(id[0], id[1], body));
    }

    /** D05 轮换凭证；新密钥只在本次响应出现。 */
    @PostMapping("/credentials/{credentialId}/rotation")
    public ResponseEntity<ClientApiCredentialSecretResponseBody> rotate(HttpServletRequest request,
        @PathVariable String credentialId, @Valid @RequestBody RotateClientApiCredentialRequestBody body) {
        var id = sessions.identity(request);
        return relay(credentials.rotate(id[0], id[1], credentialId, body));
    }

    /** D06 撤销凭证。 */
    @PostMapping("/credentials/{credentialId}/revocation")
    public ClientApiCredentialResponseBody revoke(HttpServletRequest request, @PathVariable String credentialId,
        @Valid @RequestBody RevokeClientApiCredentialRequestBody body) {
        var id = sessions.identity(request);
        return credentials.revoke(id[0], id[1], credentialId, body);
    }

    /** D07 Webhook 端点列表。 */
    @GetMapping("/webhook-endpoints")
    public ClientWebhookEndpointsResponseBody endpoints(HttpServletRequest request) {
        var id = sessions.identity(request);
        return webhooks.endpoints(id[0], id[1]);
    }

    /** D08 新建端点；签名密钥只在本次响应出现。 */
    @PostMapping("/webhook-endpoints")
    public ResponseEntity<CreateWebhookEndpointResponseBody> createEndpoint(HttpServletRequest request,
        @Valid @RequestBody ClientCreateWebhookEndpointRequestBody body) {
        var id = sessions.identity(request);
        return relay(webhooks.createEndpoint(id[0], id[1], body));
    }

    /** D09 修改端点。 */
    @PutMapping("/webhook-endpoints/{endpointId}")
    public WebhookEndpointMutationResponseBody updateEndpoint(HttpServletRequest request, @PathVariable String endpointId,
        @Valid @RequestBody ClientSaveWebhookEndpointRequestBody body) {
        var id = sessions.identity(request);
        return webhooks.updateEndpoint(id[0], id[1], endpointId, body);
    }

    /** D10 启用 / 停用端点。 */
    @PostMapping("/webhook-endpoints/{endpointId}/status")
    public WebhookEndpointMutationResponseBody changeStatus(HttpServletRequest request, @PathVariable String endpointId,
        @Valid @RequestBody ClientChangeWebhookEndpointStatusRequestBody body) {
        var id = sessions.identity(request);
        return webhooks.changeEndpointStatus(id[0], id[1], endpointId, body);
    }

    /** D11 轮换签名密钥；新密钥只在本次响应出现。 */
    @PostMapping("/webhook-endpoints/{endpointId}/secret-rotation")
    public ResponseEntity<RotateWebhookSecretResponseBody> rotateSecret(HttpServletRequest request,
        @PathVariable String endpointId, @Valid @RequestBody ClientRotateWebhookSecretRequestBody body) {
        var id = sessions.identity(request);
        return relay(webhooks.rotateSecret(id[0], id[1], endpointId, body));
    }

    /** D12 发送 webhook.ping 测试事件；无 Body。 */
    @PostMapping("/webhook-endpoints/{endpointId}/test-events")
    public WebhookTestResponseBody test(HttpServletRequest request, @PathVariable String endpointId) {
        var id = sessions.identity(request);
        return webhooks.testEndpoint(id[0], id[1], endpointId);
    }

    /** D13 投递列表；页面的 from/to 对应 Platform 的创建时间区间。 */
    @GetMapping("/webhook-deliveries")
    public PagingResponseBody<WebhookDeliverySummaryResponseBody> deliveries(HttpServletRequest request,
        @RequestParam(value = "endpointId", required = false) String endpointId,
        @RequestParam(value = "eventType", required = false) String eventType,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        var id = sessions.identity(request);
        return webhooks.deliveries(id[0], id[1], from, to, page, size, endpointId, eventType, status);
    }

    /** D14 投递详情。 */
    @GetMapping("/webhook-deliveries/{deliveryId}")
    public ClientWebhookDeliveryDetailResponseBody delivery(HttpServletRequest request, @PathVariable String deliveryId) {
        var id = sessions.identity(request);
        return webhooks.delivery(id[0], id[1], deliveryId);
    }

    /** D15 重发单条投递；无 Body。 */
    @PostMapping("/webhook-deliveries/{deliveryId}/replay")
    public ResponseEntity<ReplayWebhookDeliveryResponseBody> replay(HttpServletRequest request, @PathVariable String deliveryId) {
        var id = sessions.identity(request);
        return relay(webhooks.replayDelivery(id[0], id[1], deliveryId));
    }

    /** 保留 Owner 的状态码与 Body，不透传其他响应头。 */
    static <T> ResponseEntity<T> relay(ResponseEntity<T> owner) {
        return ResponseEntity.status(owner.getStatusCode()).body(owner.getBody());
    }
}

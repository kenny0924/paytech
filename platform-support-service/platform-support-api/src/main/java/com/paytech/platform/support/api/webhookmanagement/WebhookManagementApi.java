package com.paytech.platform.support.api.webhookmanagement;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
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

import jakarta.validation.Valid;

/**
 * Operations 代 Client 维护 Webhook 端点、订阅与签名密钥，查询投递并人工 Replay
 * <li>每次调用重检普通 Operations 会话；读需要 WEBHOOK_READ，写需要 WEBHOOK_MANAGE</li>
 * <li>写请求带 reason，每次写入记 Platform 审计；请求幂等归 Gateway</li>
 * <li>新建与轮换的响应含密钥明文，整个前缀只记元数据日志且不缓存</li>
 */
@HttpExchange("/webhook-admin/v1")
public interface WebhookManagementApi {

    /**
     * 按 Client ID 或名称搜索已开通平台访问的 Client
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param keyword 可空；去除首尾空白后最多 255 字符；1—18 位数字时按 Client ID 精确匹配，否则按名称包含匹配
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10；支持 10、20、50、100、200
     * @return 按租户 ID 升序的 Client 与端点数量；不过滤已暂停的 Client
     */
    @GetExchange("/clients")
    PagingResponseBody<WebhookClientResponseBody> clients(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询 Client 的全部端点（最多 5 个）
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param clientId Client ID；未开通平台访问返回 400
     * @return 按创建时间升序的端点，不含密钥值
     */
    @GetExchange("/clients/{clientId}/endpoints")
    List<WebhookEndpointResponseBody> endpoints(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("clientId") String clientId);

    /**
     * 新建端点并生成首个签名密钥
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param clientId Client ID；未开通平台访问返回 400，已有 5 个端点返回 400
     * @param request 地址、订阅事件类型与原因
     * @return 201，端点 ID 与只出现一次的密钥明文
     */
    @PostExchange("/clients/{clientId}/endpoints")
    ResponseEntity<CreateWebhookEndpointResponseBody> createEndpoint(
        @RequestHeader("X-Gateway-Actor-Subject") String actor, @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("clientId") String clientId, @Valid @RequestBody SaveWebhookEndpointRequestBody request);

    /**
     * 修改端点地址与订阅事件类型
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param endpointId 端点 ID，不存在返回 404
     * @param request 地址、订阅事件类型、当前版本与原因；版本不符返回 409
     * @return 端点 ID 与新版本
     */
    @PutExchange("/endpoints/{endpointId}")
    WebhookEndpointMutationResponseBody updateEndpoint(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("endpointId") String endpointId,
        @Valid @RequestBody SaveWebhookEndpointRequestBody request);

    /**
     * 启用或停用端点；停用后未完成的投递被取出时取消，重新启用不自动补发
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param endpointId 端点 ID，不存在返回 404
     * @param request 目标状态、当前版本与原因；状态未变化返回 400
     * @return 端点 ID 与新版本
     */
    @PatchExchange("/endpoints/{endpointId}/status")
    WebhookEndpointMutationResponseBody changeEndpointStatus(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("endpointId") String endpointId,
        @Valid @RequestBody ChangeWebhookEndpointStatusRequestBody request);

    /**
     * 轮换签名密钥；默认旧密钥再参与签名 1 小时
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param endpointId 端点 ID，不存在返回 404
     * @param request 是否立即作废与原因
     * @return 新密钥序号、只出现一次的明文与旧密钥宽限截止时间；不缓存
     */
    @PostExchange("/endpoints/{endpointId}/signing-secret/rotations")
    ResponseEntity<RotateWebhookSecretResponseBody> rotateSecret(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("endpointId") String endpointId,
        @Valid @RequestBody RotateWebhookSecretRequestBody request);

    /**
     * 同步发送一次签名的 webhook.ping 测试事件；不落事件或投递记录
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param endpointId 端点 ID，不存在返回 404；停用的端点也可测试
     * @return 端点响应状态或失败原因
     */
    @PostExchange("/endpoints/{endpointId}/tests")
    WebhookTestResponseBody testEndpoint(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("endpointId") String endpointId);

    /**
     * 按创建时间倒序分页查询 Client 的投递
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param clientId 必填 Client ID
     * @param createdFrom 必填 UTC ISO-8601 起始时间（含）
     * @param createdTo 必填 UTC ISO-8601 截止时间（不含），跨度不超过 31 天
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10
     * @param endpointId 可选端点 ID
     * @param eventType 可选事件类型
     * @param status 可选投递状态
     * @param eventId 可选公开 eventId
     * @return 投递分页
     */
    @GetExchange("/deliveries")
    PagingResponseBody<WebhookDeliverySummaryResponseBody> deliveries(
        @RequestHeader("X-Gateway-Actor-Subject") String actor, @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam("clientId") String clientId, @RequestParam("createdFrom") String createdFrom,
        @RequestParam("createdTo") String createdTo, @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "endpointId", required = false) String endpointId,
        @RequestParam(value = "eventType", required = false) String eventType,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "eventId", required = false) String eventId);

    /**
     * 读取投递详情
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param deliveryId 投递 ID，不存在返回 404
     * @return 投递、冻结事件与全部尝试
     */
    @GetExchange("/deliveries/{deliveryId}")
    WebhookDeliveryDetailResponseBody delivery(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("deliveryId") String deliveryId);

    /**
     * 对已结束的投递生成新投递；Event ID 与 Body 不变，使用当前地址与密钥
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param deliveryId 原投递 ID，不存在返回 404；进行中返回 400；端点未启用返回 400
     * @param request 原因
     * @return 201，新投递 ID
     */
    @PostExchange("/deliveries/{deliveryId}/replays")
    ResponseEntity<ReplayWebhookDeliveryResponseBody> replayDelivery(
        @RequestHeader("X-Gateway-Actor-Subject") String actor, @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("deliveryId") String deliveryId, @Valid @RequestBody ReplayWebhookDeliveryRequestBody request);
}

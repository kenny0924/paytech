package com.paytech.platform.support.api.webhook;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.webhook.request.SubmitWebhookEventRequestBody;

import jakarta.validation.Valid;

/**
 * Client Webhook 事件受理契约；来源系统在本地事务提交后经私网调用，不经过 Gateway 与 BFF。
 * <li>X-Caller-Service 为过渡信任：部署必须限制私网访问，它不是强身份认证。</li>
 * <li>无 Idempotency-Key；去重依据为 Client + 调用方 + envelope.eventId，超时后原样重试。</li>
 */
@HttpExchange(url = "/webhooks/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface WebhookEventApi {

    /** 调用方服务名 Header。 */
    String CALLER_SERVICE_HEADER = "X-Caller-Service";

    /**
     * 受理冻结事件并按订阅生成投递
     *
     * @param callerService
     *            调用方服务名，须在 webhook.http.callers 中登记且被授权该事件类型，并作为来源系统保存
     * @param requestBody
     *            Client、来源版本、两个摘要与 Envelope；Body 不超过 8 KiB
     * @return 无 Body
     *         <li>204 表示已受理（含同一事件内容相同的重复提交），调用方可完成本地任务；不表示已送达 Client。</li>
     *         <li>409 表示同一 eventId 已以不同内容受理，调用方不得改内容重试。</li>
     *         <li>400/422 不重试；503 未写库，可原样重试。</li>
     */
    @PostExchange(url = "/events", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> submitEvent(@RequestHeader(CALLER_SERVICE_HEADER) String callerService,
        @Valid @RequestBody SubmitWebhookEventRequestBody requestBody);
}

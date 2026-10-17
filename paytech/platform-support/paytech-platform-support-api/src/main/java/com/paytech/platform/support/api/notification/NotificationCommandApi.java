package com.paytech.platform.support.api.notification;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.platform.support.api.notification.request.SubmitNotificationRequestBody;
import com.paytech.platform.support.api.notification.response.SubmitNotificationResponseBody;

import jakarta.validation.Valid;

/**
 * 普通通知内部提交契约；业务系统在本地事务提交后经私网调用，不经过 Gateway 与 BFF。
 * <li>X-Caller-Service 为过渡信任：部署必须限制私网访问，它不是强身份认证。</li>
 * <li>无 Idempotency-Key；去重依据为调用方 + sourceEventKey，超时后用原 sourceEventKey 重试。</li>
 */
@HttpExchange(url = "/notifications/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface NotificationCommandApi {

    /** 调用方服务名 Header。 */
    String CALLER_SERVICE_HEADER = "X-Caller-Service";

    /**
     * 提交普通通知
     *
     * @param callerService
     *            调用方服务名，须在 notification.http.callers 中登记，并作为通知来源系统保存
     * @param requestBody
     *            模板、语言、参数与收件人；Body 不超过 1 MB
     * @return 受理结果
     *         <li>200 表示已受理，不表示邮件已发出或送达。</li>
     *         <li>409 表示同一调用方的该 sourceEventKey 已受理过，调用方应视为成功，不得换新 key 重发。</li>
     *         <li>400/413/422 不重试；503 未写库，可用原 sourceEventKey 重试。</li>
     */
    @PostExchange("/requests")
    SubmitNotificationResponseBody submitNotification(@RequestHeader(CALLER_SERVICE_HEADER) String callerService,
        @Valid @RequestBody SubmitNotificationRequestBody requestBody);
}

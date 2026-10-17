package com.paytech.platform.support.ext.rest.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs;
import com.paytech.platform.support.api.webhook.WebhookEventApi;
import com.paytech.platform.support.api.webhook.request.SubmitWebhookEventRequestBody;
import com.paytech.platform.support.configuration.WebhookCallerProperties;
import com.paytech.platform.support.ext.rest.internalauth.InternalCallerResolver;
import com.paytech.platform.support.service.processor.webhook.SubmitWebhookEventProcessor;
import com.paytech.platform.support.service.processor.webhook.request.SubmitWebhookEventRequest;

import lombok.AllArgsConstructor;

/**
 * Webhook 事件内部提交的协议适配；不开事务。
 * <li>先按白名单校验调用方与 envelope.eventType，未通过时不解析租户、不写库</li>
 * <li>调用方服务名作为来源系统交给处理器；携带已验证内部 Token 时以 Token sub 为准（D26）</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "webhook.enabled", havingValue = "true")
public class WebhookEventController implements WebhookEventApi {

    private final InternalCallerResolver internalCallers;
    private final WebhookCallerProperties callers;
    private final SubmitWebhookEventProcessor processor;

    /** 授权后转换并委托受理；已受理（含内容相同的重复提交）返回 204。 */
    @Override
    public ResponseEntity<Void> submitEvent(String headerCaller, SubmitWebhookEventRequestBody requestBody) {
        String callerService = internalCallers.resolve(headerCaller);
        var eventType = requestBody.getEnvelope().path("eventType");
        callers.authorize(callerService, eventType.isString() ? eventType.stringValue() : null);
        processor.process(new SubmitWebhookEventRequest(callerService, ClientApiManagementInputs.id(requestBody.getClientId()),
            Math.toIntExact(requestBody.getSourceVersion()), requestBody.getSourcePayloadHash(),
            requestBody.getPayloadHash(), requestBody.getEnvelope()));
        return ResponseEntity.noContent().build();
    }
}

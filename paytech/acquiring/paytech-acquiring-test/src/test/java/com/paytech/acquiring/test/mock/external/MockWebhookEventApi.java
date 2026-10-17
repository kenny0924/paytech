package com.paytech.acquiring.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.acquiring.test.mocker.external.PlatformWebhookMocker;
import com.paytech.platform.support.api.webhook.WebhookEventApi;
import com.paytech.platform.support.api.webhook.request.SubmitWebhookEventRequestBody;

import lombok.AllArgsConstructor;

/** 独立 Mock 服务直接实现 Platform Webhook 受理合同，Body 校验沿用正式 API 注解。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockWebhookEventApi implements WebhookEventApi, WebMvcConfigurer {
    private final PlatformWebhookMocker webhooks;

    /** 模拟提供方应用的 Context Path，接口内路径仍由正式 API 定义。 */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/platform-support", type -> WebhookEventApi.class.isAssignableFrom(type));
    }

    /** 委托 Mocker 取出本次响应；受理返回无 Body 的 204，故障写出与 Problem Body 一致的 Request ID。 */
    @Override
    public ResponseEntity<Void> submitEvent(String callerService, SubmitWebhookEventRequestBody requestBody) {
        var reply = webhooks.next(callerService, requestBody);
        if (reply.isFault()) {
            var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
            attributes.getResponse().setHeader("X-Request-Id", PlatformWebhookMocker.ERROR_REQUEST_ID);
            throw reply.fault();
        }
        return ResponseEntity.noContent().build();
    }
}

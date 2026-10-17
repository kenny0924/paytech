package com.paytech.platform.support.test.mock.external;

import java.io.IOException;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.platform.support.delivery.domain.WebhookSignature;
import com.paytech.platform.support.test.mocker.external.WebhookReceiverMocker;
import com.paytech.platform.support.test.mocker.external.WebhookReceiverMocker.Received;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 独立 Mock HTTP 服务上的合成 Client Webhook 接收端；记录请求后按 WebhookReceiverMocker 的配置响应。 */
@RestController
@TestComponent
@RequiredArgsConstructor
public class WebhookReceiverMockController {

    /** 接收端在 Mock 服务上的路径前缀。 */
    public static final String PREFIX = "/webhook-receiver/";

    private final WebhookReceiverMocker receiver;

    /** 任意方法都记录，便于发现错误的请求方法。 */
    @RequestMapping(PREFIX + "{name}")
    public ResponseEntity<String> receive(@PathVariable("name") String name, HttpServletRequest request)
        throws IOException, InterruptedException {
        var reply = receiver.next(new Received(name, request.getMethod(), request.getContentType(),
            request.getHeader(HttpHeaders.USER_AGENT), request.getHeader(WebhookSignature.HEADER),
            request.getInputStream().readAllBytes()));
        if (reply.disconnect()) {
            throw MockHttpFault.disconnect();
        }
        if (reply.delayMs() > 0) {
            Thread.sleep(reply.delayMs());
        }
        if (reply.status() >= 400) {
            throw MockHttpFault.raw(reply.status(), "{}");
        }
        return ResponseEntity.status(reply.status()).body("{}");
    }
}

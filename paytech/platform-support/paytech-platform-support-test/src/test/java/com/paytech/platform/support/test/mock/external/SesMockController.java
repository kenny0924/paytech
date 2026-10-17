package com.paytech.platform.support.test.mock.external;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.TreeMap;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.platform.support.test.mocker.external.SesMocker;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

/** 独立 Mock HTTP 服务上的合成 SES v2 终端：前缀下的请求交给 SesMocker 按收件人响应，不校验签名。 */
@RestController
@TestComponent
@RequiredArgsConstructor
public class SesMockController {

    /** SES 终端覆盖在 Mock 服务上的路径前缀。 */
    public static final String PREFIX = "/ses";

    private final SesMocker ses;

    /** 记录请求后返回配置的响应；错误同时写 x-amzn-ErrorType Header 与 __type，经 MockHttpFault 写出原文。 */
    @RequestMapping(PREFIX + "/**")
    public ResponseEntity<String> handle(HttpServletRequest request, HttpServletResponse response)
        throws IOException, InterruptedException {
        var headers = new TreeMap<String, String>();
        Collections.list(request.getHeaderNames()).forEach(name ->
            headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name)));
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var reply = ses.next(request.getMethod(), request.getRequestURI().substring(PREFIX.length()), headers, body);
        if (reply.disconnect()) {
            throw MockHttpFault.disconnect();
        }
        if (reply.delayMs() > 0) {
            Thread.sleep(reply.delayMs());
        }
        if (reply.status() >= 400) {
            response.setHeader("x-amzn-ErrorType", reply.errorType());
            throw MockHttpFault.raw(reply.status(), reply.body());
        }
        return ResponseEntity.status(reply.status()).contentType(MediaType.APPLICATION_JSON).body(reply.body());
    }
}

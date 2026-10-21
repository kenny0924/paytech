package com.paytech.channel.connector.test.mock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.TreeMap;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.test.mocker.ChannelMocker;
import com.paytech.foundation.testsupport.api.MockHttpFault;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

/** 独立 Mock HTTP 服务上的合成外部渠道：前缀下的任意路径与方法交给 ChannelMocker 按配置响应，不装入被测应用路由。 */
@RestController
@TestComponent
@RequiredArgsConstructor
public class ChannelMockController {

    /** 渠道出站地址在 Mock 服务上的路径前缀。 */
    public static final String PREFIX = "/channel";

    private final ChannelMocker channel;

    /**
     * 记录原始路径、查询串、Header 与请求体，再返回配置的响应；4xx/5xx 以 MockHttpFault 写出原文
     *
     * @param request 渠道收到的原始请求
     * @return 配置的响应
     * @throws IOException 读取请求体失败
     * @throws InterruptedException 延迟响应被中断
     */
    @RequestMapping(PREFIX + "/**")
    public ResponseEntity<byte[]> handle(HttpServletRequest request) throws IOException, InterruptedException {
        var headers = new TreeMap<String, String>();
        Collections.list(request.getHeaderNames())
            .forEach(name -> headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name)));
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var reply = channel.next(new ChannelMocker.Received(request.getMethod(),
            request.getRequestURI().substring(PREFIX.length()), request.getQueryString(), headers, body));
        if (reply.disconnect()) {
            throw MockHttpFault.disconnect();
        }
        if (reply.delayMs() > 0) {
            Thread.sleep(reply.delayMs());
        }
        if (reply.status() >= 400) {
            // Mock 服务把 Controller 正常写出的错误状态视为意外，配置的渠道错误必须经 MockHttpFault 写出。
            throw MockHttpFault.raw(reply.status(), reply.body());
        }
        var responseHeaders = new HttpHeaders();
        reply.headers().forEach(responseHeaders::set);
        return ResponseEntity.status(reply.status()).headers(responseHeaders)
            .body(reply.body().getBytes(StandardCharsets.UTF_8));
    }
}

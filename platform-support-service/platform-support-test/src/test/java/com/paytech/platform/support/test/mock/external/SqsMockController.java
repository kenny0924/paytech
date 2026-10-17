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
import com.paytech.platform.support.test.mocker.external.SqsMocker;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** 独立 Mock HTTP 服务上的合成 SQS 终端（AWS JSON 1.0）：前缀下的请求交给 SqsMocker，不校验签名。 */
@RestController
@TestComponent
@RequiredArgsConstructor
public class SqsMockController {

    /** SQS 终端覆盖在 Mock 服务上的路径前缀。 */
    public static final String PREFIX = "/sqs";

    private static final MediaType AWS_JSON = MediaType.parseMediaType("application/x-amz-json-1.0");

    private final SqsMocker sqs;

    /** 记录请求后返回配置的响应；错误同时写 x-amzn-query-error Header，经 MockHttpFault 写出原文。 */
    @RequestMapping(PREFIX + "/**")
    public ResponseEntity<String> handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var headers = new TreeMap<String, String>();
        Collections.list(request.getHeaderNames()).forEach(name ->
            headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name)));
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var reply = sqs.next(headers.get("x-amz-target"), headers, body);
        if (reply.status() >= 400) {
            response.setHeader("x-amzn-query-error", reply.errorType() + ";Sender");
            throw MockHttpFault.raw(reply.status(), reply.body());
        }
        return ResponseEntity.status(reply.status()).contentType(AWS_JSON).body(reply.body());
    }
}

package com.paytech.account.test.mock.external;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Mock 服务的响应 Request ID：公共客户端按 Problem Profile 校验响应头与 Problem 体的 requestId 一致。 */
final class MockRequestId {

    private MockRequestId() {
    }

    /** 给当前 Mock 响应写入固定 Request ID。 */
    static void mark(String requestId) {
        var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", requestId);
    }
}

package com.paytech.billing.test.mock.external;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.paytech.billing.test.mocker.external.DtoResponseMocker;

/** Mock 服务的响应 Request ID：公共客户端按 Problem Profile 校验响应头与 Problem 体的 requestId 一致，Mock 响应统一写入固定值。 */
final class MockRequestId {

    private MockRequestId() {
    }

    /** 给当前 Mock 响应写入 {@link DtoResponseMocker#REQUEST_ID}。 */
    static void mark() {
        var attributes = (ServletRequestAttributes)RequestContextHolder.currentRequestAttributes();
        attributes.getResponse().setHeader("X-Request-Id", DtoResponseMocker.REQUEST_ID);
    }
}

package com.paytech.foundation.testsupport.api;

/** 测试 Controller 主动配置的 HTTP 故障，由独立 Mock 服务写出，不能用于业务代码。 */
public final class MockHttpFault extends RuntimeException {
    final int status;
    final Object body;
    final String rawBody;
    final boolean disconnect;

    /** 故障配置只表达本次响应，不包含业务匹配或重试规则。 */
    private MockHttpFault(int status, Object body, String rawBody, boolean disconnect) {
        super("已配置的 Mock HTTP 故障", null, false, false);
        this.status = status;
        this.body = body;
        this.rawBody = rawBody;
        this.disconnect = disconnect;
    }

    /** 返回明确配置的 JSON 错误 DTO，4xx/5xx 使用项目 Problem 媒体类型。 */
    public static MockHttpFault json(int status, Object body) {
        return new MockHttpFault(status, body, null, false);
    }

    /** 返回指定原文，供真实客户端验证畸形 JSON 等协议错误。 */
    public static MockHttpFault raw(int status, String body) {
        return new MockHttpFault(status, null, body, false);
    }

    /** 不发送 HTTP 响应，直接断开连接以触发真实传输错误。 */
    public static MockHttpFault disconnect() {
        return new MockHttpFault(0, null, null, true);
    }
}

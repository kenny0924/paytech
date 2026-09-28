package com.paytech.gateway.web;

/** 屏蔽 BFF transport 细节的固定、无 cause 协议失败标记。 */
public final class GatewayUpstreamProtocolException extends RuntimeException {
    public GatewayUpstreamProtocolException() {
        super("Operations BFF 传输协议响应无效");
    }
}

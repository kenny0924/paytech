package com.paytech.gateway.web;

/** Gateway 在已取得并发租约后执行完整下游链时产生的精确路由超时。 */
public final class GatewayUpstreamTimeoutException extends RuntimeException {
    public GatewayUpstreamTimeoutException() {
        super("Operations 下游响应超过路由超时预算");
    }
}

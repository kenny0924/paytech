package com.paytech.gateway.web;

import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;

/** Gateway 全局过滤器的固定相对顺序。 */
public final class GatewayGlobalFilterOrder {
    public static final int TRUSTED_REQUEST_CONTEXT = Ordered.HIGHEST_PRECEDENCE + 4;
    public static final int REQUEST_HEADER_LIMIT = Ordered.HIGHEST_PRECEDENCE + 5;
    public static final int DYNAMIC_ROUTE = Ordered.HIGHEST_PRECEDENCE + 12;
    public static final int REQUEST_POLICY = Ordered.HIGHEST_PRECEDENCE + 18;
    public static final int OPERATIONS_BOUNDARY = Ordered.HIGHEST_PRECEDENCE + 20;
    public static final int INGRESS_GUARD = Ordered.HIGHEST_PRECEDENCE + 30;
    public static final int TYPED_AUTHENTICATED_IDENTITY = Ordered.HIGHEST_PRECEDENCE + 40;
    public static final int AUTHENTICATED_GUARD = Ordered.HIGHEST_PRECEDENCE + 50;
    public static final int CLIENT_API_SCOPE = Ordered.HIGHEST_PRECEDENCE + 60;
    public static final int REQUEST_BODY_LIMIT = Ordered.HIGHEST_PRECEDENCE + 100;
    public static final int IDEMPOTENCY = Ordered.HIGHEST_PRECEDENCE + 150;
    public static final int UPSTREAM_TIMEOUT = Ordered.HIGHEST_PRECEDENCE + 200;
    public static final int UPSTREAM_REQUEST = Ordered.HIGHEST_PRECEDENCE + 300;
    public static final int TRUSTED_DOWNSTREAM_IDENTITY = Ordered.HIGHEST_PRECEDENCE + 400;
    public static final int OWNER_PROBLEM_RESPONSE = NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    public static final int OPERATIONS_RESPONSE_CONTRACT = NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER + 1;
    public static final int NETTY_ROUTING = Ordered.LOWEST_PRECEDENCE;

    private GatewayGlobalFilterOrder() {}
}

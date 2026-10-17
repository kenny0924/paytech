package com.paytech.gateway.web;

/** Gateway 对外暴露的稳定协议路径前缀。 */
public final class GatewayPaths {
    public static final String OPERATIONS_API_PREFIX = "/api/operations";
    public static final String OPERATIONS_AUTH_PREFIX = OPERATIONS_API_PREFIX + "/auth/v1";

    private GatewayPaths() {}
}

package com.paytech.gateway.test;

import com.paytech.gateway.web.GatewayPaths;

/** Operations Gateway 自动测试共用的已注册路径。 */
public final class OperationsTestPaths {
    public static final String LOGIN = GatewayPaths.OPERATIONS_AUTH_PREFIX + "/login";
    public static final String REFRESH = GatewayPaths.OPERATIONS_AUTH_PREFIX + "/refresh";
    public static final String LOGOUT = GatewayPaths.OPERATIONS_AUTH_PREFIX + "/logout";
    public static final String SESSION = GatewayPaths.OPERATIONS_AUTH_PREFIX + "/session";
    public static final String AUTH_COOKIE_PATH = GatewayPaths.OPERATIONS_AUTH_PREFIX;

    private OperationsTestPaths() {}
}

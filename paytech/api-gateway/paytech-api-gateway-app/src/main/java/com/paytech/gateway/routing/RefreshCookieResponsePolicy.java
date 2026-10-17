package com.paytech.gateway.routing;

/** Operations 路由允许 BFF 返回的 Refresh Cookie Max-Age 语义。 */
public enum RefreshCookieResponsePolicy {
    NONE, ISSUE_ONLY, ISSUE_OR_CLEAR, CLEAR_ONLY
}

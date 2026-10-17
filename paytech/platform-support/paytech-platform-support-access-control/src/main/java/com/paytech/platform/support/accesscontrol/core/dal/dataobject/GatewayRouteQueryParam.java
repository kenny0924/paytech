package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

/**
 * Gateway Route Query Parameter 查询投影。
 */
public record GatewayRouteQueryParam(
    /**
     * 拥有该 Query Parameter 规则的 Gateway Route 主键。
     */
    Long routeId,

    /**
     * 允许出现在请求中的 Query Parameter 名称，不保存参数值。
     */
    String parameterName) {
}

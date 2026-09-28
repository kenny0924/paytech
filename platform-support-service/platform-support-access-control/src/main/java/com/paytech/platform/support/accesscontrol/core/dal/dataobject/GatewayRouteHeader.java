package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import com.paytech.platform.support.accesscontrol.domain.enums.GatewayRouteHeaderUse;

/**
 * Gateway Route Header 查询投影。
 */
public record GatewayRouteHeader(
    /**
     * 拥有该 Header 规则的 Gateway Route 主键。
     */
    Long routeId,

    /**
     * 大小写不敏感、完整名称匹配的 HTTP Header 名称。
     */
    String headerName,

    /**
     * Header 用于 CORS 请求白名单或目标请求转发白名单。
     */
    GatewayRouteHeaderUse headerUse) {
}

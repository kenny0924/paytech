package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import com.paytech.platform.support.api.enums.*;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;

/**
 * Gateway Route 查询投影。
 */
public record GatewayRoute(
    /**
     * Gateway Route 的 Snowflake 主键。
     */
    Long routeId,

    /**
     * 跨配置变更保持稳定并下发给 Gateway/BFF 的 Route 编码。
     */
    String routeCode,

    /**
     * Route 所属外部渠道，必须与目标渠道一致。
     */
    GatewayRouteChannel channel,

    /**
     * Route 精确匹配的 HTTP Method。
     */
    GatewayHttpMethod httpMethod,

    /**
     * Gateway 对外匹配的绝对 Path Template。
     */
    String externalPathTemplate,

    /**
     * 转发给目标服务的 Path Template，变量集合必须与外部模板一致。
     */
    String downstreamPathTemplate,

    /**
     * 同渠道 Gateway Target 的本库主键。
     */
    Long targetId,

    /**
     * Gateway 预编译安全实现的选择编码，不包含算法或密钥。
     */
    GatewaySecurityProfileCode securityProfileCode,

    /**
     * Route 使用的互斥授权模式。
     */
    GatewayAuthorizationMode authorizationMode,

    /**
     * HUMAN_PERMISSION Route 必填的 Platform Permission 主键，其他模式为空。
     */
    Long permissionId,

    /**
     * API_SCOPE Route 必填的 Client API Scope Code，其他模式为空。
     */
    String requiredScopeCode,

    /**
     * 请求 Refresh Cookie 的固定协议处理模式。
     */
    GatewayRequestCookieMode requestCookieMode,

    /**
     * 响应 Refresh Cookie 的固定协议处理模式。
     */
    GatewayResponseCookieMode responseCookieMode,

    /**
     * Route 允许接收的请求体最大字节数。
     */
    long bodyLimitBytes,

    /**
     * 完整接收请求体的超时毫秒数。
     */
    int bodyReceiveTimeoutMillis,

    /**
     * 等待目标服务完整响应的超时毫秒数。
     */
    int responseTimeoutMillis,

    /**
     * Route 的当前启停状态。
     */
    GatewayConfigurationStatus status) {
}

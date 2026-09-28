package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.api.enums.*;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;

/** Gateway Route Root、Target 与 Permission 的管理查询投影。 */
public record GatewayRouteManagement(Long routeId, String routeCode, GatewayRouteChannel channel,
    GatewayHttpMethod httpMethod, String externalPathTemplate, String downstreamPathTemplate, Long targetId,
    String targetServiceCode, GatewayConfigurationStatus targetStatus, GatewaySecurityProfileCode securityProfileCode,
    GatewayAuthorizationMode authorizationMode, Long permissionId, String permissionCode, String requiredScopeCode,
    GatewayRequestCookieMode requestCookieMode, GatewayResponseCookieMode responseCookieMode, long bodyLimitBytes,
    int bodyReceiveTimeoutMillis, int responseTimeoutMillis, GatewayConfigurationStatus status, int version,
    Long createdBy, Long updatedBy, Instant createTime, Instant updateTime) {
}

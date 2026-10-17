package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import com.paytech.platform.support.api.enums.GatewayAuthorizationMode;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;

/** Route 状态命令排他读取使用的单表 Root 投影。 */
public record GatewayRouteRoot(Long routeId, String routeCode, Long targetId, GatewayAuthorizationMode authorizationMode,
    Long permissionId, GatewayConfigurationStatus status, int version) {
}

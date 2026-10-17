package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;

/** Gateway Target 管理查询与并发写入投影。 */
public record GatewayTargetManagement(Long targetId, String targetServiceCode, String discoveryServiceName,
    GatewayRouteChannel channel, GatewayConfigurationStatus status, int version, Long createdBy, Long updatedBy,
    Instant createTime, Instant updateTime) {
}

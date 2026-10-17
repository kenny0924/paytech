package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.*;

/**
 * Gateway 当前配置集合查询。
 */
public interface GatewayConfigurationMapper {

    List<GatewayTarget> selectTargets();

    List<GatewayRoute> selectRoutes();

    List<PermissionProjection> selectPermissionsByIds(@Param("permissionIds") Set<Long> permissionIds);

    List<GatewayRouteHeader> selectHeaders();

    List<GatewayRouteQueryParam> selectQueryParameters();

    List<GatewayGuardRule> selectGuardRules();

    record PermissionProjection(Long permissionId, String permissionCode) {
    }
}

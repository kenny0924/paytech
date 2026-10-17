package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.*;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.GatewayConfigurationMapper;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.*;
import com.paytech.platform.support.accesscontrol.domainservice.repository.GatewayConfigurationRepository;
import com.paytech.platform.support.api.enums.GatewayAuthorizationMode;

import lombok.AllArgsConstructor;

/**
 * 基于 MyBatis 的 Gateway 当前配置仓储。
 */
@Repository
@AllArgsConstructor
public class GatewayConfigurationRepositoryImpl implements GatewayConfigurationRepository {

    private final GatewayConfigurationMapper mapper;

    @Override
    public LoadedConfiguration loadCurrentConfiguration() {
        var targets = mapper.selectTargets();
        var routes = mapper.selectRoutes();
        Set<Long> permissionIds = routes.stream()
            .filter(route -> route.authorizationMode() == GatewayAuthorizationMode.HUMAN_PERMISSION)
            .map(GatewayRoute::permissionId).filter(java.util.Objects::nonNull).collect(Collectors.toUnmodifiableSet());
        var permissions = mapper.selectPermissionsByIds(permissionIds);
        var headers = mapper.selectHeaders();
        var queryParameters = mapper.selectQueryParameters();
        var guardRules = mapper.selectGuardRules();

        return new LoadedConfiguration(targets.stream().map(this::targetRow).toList(),
            routes.stream().map(this::routeRow).toList(),
            permissions.stream()
                .map(permission -> new PermissionRow(permission.permissionId(), permission.permissionCode())).toList(),
            headers.stream().map(this::headerRow).toList(),
            queryParameters.stream().map(this::queryParameterRow).toList(),
            guardRules.stream().map(this::guardRuleRow).toList());
    }

    private TargetRow targetRow(GatewayTarget target) {
        return new TargetRow(target.targetId(), target.targetServiceCode(), target.discoveryServiceName(),
            target.channel().getCode(), target.status().getCode());
    }

    private RouteRow routeRow(GatewayRoute route) {
        return new RouteRow(route.routeId(), route.routeCode(), route.channel().getCode(), route.httpMethod().getCode(),
            route.externalPathTemplate(), route.downstreamPathTemplate(), route.targetId(),
            route.securityProfileCode().getCode(), route.authorizationMode().getCode(), route.permissionId(),
            route.requiredScopeCode(), route.requestCookieMode().getCode(), route.responseCookieMode().getCode(),
            route.idempotencyMode().getCode(), route.bodyLimitBytes(), route.bodyReceiveTimeoutMillis(), route.responseTimeoutMillis(),
            route.status().getCode());
    }

    private HeaderRow headerRow(GatewayRouteHeader header) {
        return new HeaderRow(header.routeId(), header.headerName(), header.headerUse().getCode());
    }

    private QueryParameterRow queryParameterRow(GatewayRouteQueryParam queryParameter) {
        return new QueryParameterRow(queryParameter.routeId(), queryParameter.parameterName());
    }

    private GuardRuleRow guardRuleRow(GatewayGuardRule guardRule) {
        return new GuardRuleRow(guardRule.routeId(), guardRule.ruleType().getCode(), guardRule.dimension().getCode(),
            guardRule.capacity(), guardRule.refillTokens(), guardRule.refillPeriodMillis(),
            guardRule.concurrencyLimit());
    }
}

package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.*;
import com.paytech.foundation.id.IdGenerator;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayGuardRule;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayRouteHeader;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayRouteManagement;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayRouteQueryParam;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayRouteRoot;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayTargetManagement;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.GatewayConfigurationManagementMapper;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementState.RouteDetails;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementState.RouteQuery;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementState.TargetQuery;
import com.paytech.platform.support.accesscontrol.domainservice.repository.GatewayConfigurationManagementRepository;

import lombok.AllArgsConstructor;

/** Gateway 配置管理持久化及详情聚合。 */
@Repository
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
@AllArgsConstructor
public class GatewayConfigurationManagementRepositoryImpl implements GatewayConfigurationManagementRepository {
    private final IdGenerator ids;
    private final GatewayConfigurationManagementMapper mapper;

    @Override
    public List<GatewayTargetManagement> targets(TargetQuery query, int offset, int size) {
        return mapper.targets(query.code(), query.channel(), query.status(), offset, size);
    }

    @Override
    public long targetCount(TargetQuery query) {
        return mapper.targetCount(query.code(), query.channel(), query.status());
    }

    @Override
    public GatewayTargetManagement target(String code) {
        return mapper.target(code);
    }

    @Override
    public int updateTargetStatus(long id, GatewayConfigurationStatus status, int version, long actorId, Instant now) {
        return mapper.updateTargetStatus(id, status, version, actorId, now);
    }

    @Override
    public List<GatewayRouteManagement> routes(RouteQuery query, int offset, int size) {
        return mapper.routes(query.code(), query.channel(), query.status(), query.targetCode(), query.requiredScopeCode(), offset, size);
    }

    @Override
    public long routeCount(RouteQuery query) {
        return mapper.routeCount(query.code(), query.channel(), query.status(), query.targetCode(), query.requiredScopeCode());
    }

    @Override
    public RouteDetails route(String code) {
        GatewayRouteManagement row = mapper.route(code);
        if (row == null) {
            return null;
        }
        List<GatewayRouteHeader> headers = mapper.headers(row.routeId());
        List<String> queryParameters = mapper.queryParameters(row.routeId()).stream()
            .map(GatewayRouteQueryParam::parameterName).toList();
        List<GatewayGuardRule> guards = mapper.guardRules(row.routeId());
        return new RouteDetails(row, headers, queryParameters, guards);
    }

    @Override
    public GatewayRouteRoot routeRoot(String code) {
        return mapper.routeRoot(code);
    }

    @Override
    public int updateRouteStatus(long id, GatewayConfigurationStatus status, int version, long actorId, Instant now) {
        return mapper.updateRouteStatus(id, status, version, actorId, now);
    }

    @Override
    public boolean permissionActive(long permissionId) {
        return mapper.permissionActive(permissionId);
    }

    @Override
    public int insertTarget(TargetRow row, long actorId, Instant now) {
        return mapper.insertTarget(row, actorId, now);
    }

    @Override
    public int updateTarget(TargetRow row, int version, long actorId, Instant now) {
        return mapper.updateTarget(row, version, actorId, now);
    }

    @Override
    public int insertRoute(RouteRow row, long actorId, Instant now) {
        return mapper.insertRoute(row, actorId, now);
    }

    @Override
    public int updateRoute(RouteRow row, int version, long actorId, Instant now) {
        return mapper.updateRoute(row, version, actorId, now);
    }

    @Override
    public void replaceRouteChildren(long routeId, List<HeaderRow> headers,
        List<QueryParameterRow> queries, List<GuardRuleRow> guards) {
        mapper.deleteHeaders(routeId);
        mapper.deleteQueries(routeId);
        mapper.deleteGuards(routeId);
        headers.forEach(row -> mapper.insertHeader(ids.nextId(), row));
        queries.forEach(row -> mapper.insertQuery(ids.nextId(), row));
        guards.forEach(row -> mapper.insertGuard(ids.nextId(), row));
    }

    @Override
    public PermissionIdentity permissionIdentity(long id) {
        return mapper.permissionIdentity(id);
    }
}

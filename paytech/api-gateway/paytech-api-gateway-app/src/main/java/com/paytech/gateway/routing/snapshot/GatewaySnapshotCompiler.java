package com.paytech.gateway.routing.snapshot;

import java.time.Duration;
import java.util.*;

import org.springframework.http.HttpMethod;

import com.paytech.gateway.routing.runtime.GatewayPathTemplate;
import com.paytech.gateway.routing.runtime.GatewayRuntimeCatalog;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.routing.runtime.GatewayRuntimeSnapshot;
import com.paytech.gateway.routing.runtime.GatewayRuntimeTarget;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayRouteSnapshot;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayTargetSnapshot;

/** 将完整 PLT-I20 DTO 全量校验并编译为不可变运行时目录。 */
public final class GatewaySnapshotCompiler {
    private static final int MAX_TARGETS = 128;
    private static final int MAX_ROUTES = 4_096;
    private final GatewayRoutePolicyValidator routePolicyValidator = new GatewayRoutePolicyValidator();
    private final GatewayGuardProfileCompiler guardProfileCompiler = new GatewayGuardProfileCompiler();

    /** 校验整份快照并绑定同一版本的目标与路由，全部成功后才返回可发布目录。 */
    public GatewayRuntimeSnapshot compile(GatewayPublicSnapshotResponseBody source) {
        validateSnapshot(source);
        Map<String, GatewayRuntimeTarget> targets = compileTargets(source.getTargets());
        List<GatewayRuntimeRoute> routes = compileRoutes(source.getRoutes(), targets);
        rejectOverlappingRoutes(routes);
        return new GatewayRuntimeSnapshot(source.getSchemaVersion(), targets, new GatewayRuntimeCatalog(routes));
    }

    private void validateSnapshot(GatewayPublicSnapshotResponseBody source) {
        if (source == null || source.getSchemaVersion() != 1) {
            throw invalid("不支持的 Gateway Snapshot Schema Version");
        }
        if (source.getTargets() == null || source.getRoutes() == null) {
            throw invalid("Gateway Snapshot Target/Route 集合不可为空");
        }
        if (source.getTargets().size() > MAX_TARGETS || source.getRoutes().size() > MAX_ROUTES) {
            throw invalid("Gateway Snapshot Target/Route 数量非法");
        }
    }

    /** 编译唯一的逻辑目标索引，服务发现身份来自控制面。 */
    private Map<String, GatewayRuntimeTarget> compileTargets(List<GatewayTargetSnapshot> snapshots) {
        Map<String, GatewayRuntimeTarget> targets = new HashMap<>();
        for (GatewayTargetSnapshot target : snapshots) {
            GatewayRouteChannel channel =
                target == null ? null : GatewaySnapshotCodeParser.routeChannel(target.getChannel());
            if (target == null || !validStableCode(target.getTargetServiceCode()) || targets.putIfAbsent(
                target.getTargetServiceCode(),
                compileTarget(target, channel)) != null) {
                throw invalid("Gateway Target 重复或为空");
            }
        }
        return Map.copyOf(targets);
    }

    /** 将非法服务发现身份转换为整份快照拒绝，不允许 URI 或端口逃逸。 */
    private GatewayRuntimeTarget compileTarget(GatewayTargetSnapshot source, GatewayRouteChannel channel) {
        try {
            return new GatewayRuntimeTarget(source.getTargetServiceCode(), channel, source.getDiscoveryServiceName());
        } catch (IllegalArgumentException exception) {
            throw invalid("Gateway Target 服务发现名称非法");
        }
    }

    /** 将每条路由绑定到本次编译的目标，并校验精确端点唯一性。 */
    private List<GatewayRuntimeRoute> compileRoutes(List<GatewayRouteSnapshot> snapshots,
        Map<String, GatewayRuntimeTarget> targets) {
        List<GatewayRuntimeRoute> routes = new ArrayList<>();
        Set<EndpointKey> endpoints = new HashSet<>();
        Set<String> routeCodes = new HashSet<>();
        for (GatewayRouteSnapshot route : snapshots) {
            routes.add(compileRoute(route, targets, endpoints, routeCodes));
        }
        return routes;
    }

    /** 校验渠道、路径、安全档案和 Guard 后创建不可变运行路由。 */
    private GatewayRuntimeRoute compileRoute(GatewayRouteSnapshot route,
        Map<String, GatewayRuntimeTarget> targets, Set<EndpointKey> endpoints, Set<String> routeCodes) {
        if (route == null || !validStableCode(route.getRouteCode()) || !routeCodes.add(route.getRouteCode())) {
            throw invalid("Gateway Route Code 非法");
        }
        GatewayRouteChannel channel = GatewaySnapshotCodeParser.routeChannel(route.getChannel());
        HttpMethod method = method(route.getHttpMethod());
        GatewayPathTemplate externalPath = path(route.getExternalPathTemplate());
        GatewayPathTemplate downstreamPath = path(route.getDownstreamPathTemplate());
        validateTargetAndPath(route, channel, targets, externalPath, downstreamPath);
        if (!endpoints.add(new EndpointKey(channel, method, route.getExternalPathTemplate()))) {
            throw invalid("Gateway Route Channel/Method/Path 重复: " + route.getRouteCode());
        }
        routePolicyValidator.validate(route);
        GatewayGuardProfileCompiler.GuardProfiles guards =
            guardProfileCompiler.compile(route.getGuardRules(), route.getSecurityProfileCode());
        return runtimeRoute(route, channel, method, targets.get(route.getTargetServiceCode()), guards);
    }

    /** 保持路径变量一致，拒绝目标缺失或跨渠道引用。 */
    private void validateTargetAndPath(GatewayRouteSnapshot route, GatewayRouteChannel channel,
        Map<String, GatewayRuntimeTarget> targets, GatewayPathTemplate externalPath,
        GatewayPathTemplate downstreamPath) {
        if (!externalPath.variableNames().equals(downstreamPath.variableNames())) {
            throw invalid("Gateway Route 外部与下游 Path Variable 集合不一致: " + route.getRouteCode());
        }
        GatewayRuntimeTarget target = targets.get(route.getTargetServiceCode());
        if (target == null || target.channel() != channel) {
            throw invalid("Gateway Route 引用目标不存在或渠道不一致: " + route.getRouteCode());
        }
    }

    /** 冻结目标、请求策略与超时，后续请求处理不再查询目标目录。 */
    private GatewayRuntimeRoute runtimeRoute(GatewayRouteSnapshot route, GatewayRouteChannel channel, HttpMethod method,
        GatewayRuntimeTarget target, GatewayGuardProfileCompiler.GuardProfiles guards) {
        return new GatewayRuntimeRoute(route.getRouteCode(), channel, method, route.getExternalPathTemplate(),
            route.getDownstreamPathTemplate(), target, route.getSecurityProfileCode(),
            route.getAuthorizationMode(), route.getPermissionCode(), route.getRequiredScopeCode(), route.getRequestCookieMode(),
            route.getResponseCookieMode(), Set.copyOf(route.getCorsRequestHeaderNames()),
            Set.copyOf(route.getForwardedRequestHeaderNames()), Set.copyOf(route.getQueryParameterNames()),
            route.getBodyLimitBytes(), Duration.ofMillis(route.getBodyReceiveTimeoutMillis()),
            Duration.ofMillis(route.getResponseTimeoutMillis()), guards.ingress(), guards.authenticated(),
            GatewaySnapshotCodeParser.idempotencyMode(route.getIdempotencyMode()));
    }

    private void rejectOverlappingRoutes(List<GatewayRuntimeRoute> routes) {
        Map<RouteShapeKey, List<IndexedRoute>> templates = new HashMap<>();
        for (GatewayRuntimeRoute route : routes) {
            GatewayPathTemplate path = GatewayPathTemplate.compile(route.externalPath());
            // 完全固定路径优先，精确重复已由 compileRoute 拒绝；其余模板不得按变量数量消歧。
            if (path.variableCount() == 0) {
                continue;
            }
            List<GatewayPathTemplate.PathSegment> shape = path.shape();
            RouteShapeKey key = new RouteShapeKey(route.channel(), route.method(), shape.size());
            List<IndexedRoute> candidates = templates.computeIfAbsent(key, ignored -> new ArrayList<>());
            for (IndexedRoute candidate : candidates) {
                if (overlaps(candidate.segments(), shape)) {
                    throw invalid("Gateway Route Method/Path 存在可匹配冲突: " + candidate.routeCode() + " / "
                        + route.routeCode());
                }
            }
            candidates.add(new IndexedRoute(route.routeCode(), shape));
        }
    }

    private record EndpointKey(GatewayRouteChannel channel, HttpMethod method, String externalPath) {
    }

    private record RouteShapeKey(GatewayRouteChannel channel, HttpMethod method, int segmentCount) {
    }

    private record IndexedRoute(String routeCode, List<GatewayPathTemplate.PathSegment> segments) {
    }

    private boolean overlaps(List<GatewayPathTemplate.PathSegment> left, List<GatewayPathTemplate.PathSegment> right) {
        for (int index = 0; index < left.size(); index++) {
            GatewayPathTemplate.PathSegment first = left.get(index);
            GatewayPathTemplate.PathSegment second = right.get(index);
            if (!first.variable() && !second.variable() && !first.value().equals(second.value())) {
                return false;
            }
        }
        return true;
    }

    private HttpMethod method(String value) {
        return HttpMethod.valueOf(GatewaySnapshotCodeParser.httpMethod(value).getCode());
    }

    private GatewayPathTemplate path(String value) {
        try {
            return GatewayPathTemplate.compile(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("Gateway Path 非法");
        }
    }

    private boolean validStableCode(String value) {
        return value != null && value.matches("[a-z][a-z0-9-]{2,127}");
    }

    private InvalidGatewaySnapshotException invalid(String message) {
        return new InvalidGatewaySnapshotException(message);
    }
}

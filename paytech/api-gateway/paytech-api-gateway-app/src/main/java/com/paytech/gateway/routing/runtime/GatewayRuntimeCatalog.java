package com.paytech.gateway.routing.runtime;

import java.util.*;

import org.springframework.http.HttpMethod;

import com.paytech.platform.support.api.enums.GatewayRouteChannel;

/** 按精确 Method 与原始 Path 查找的不可变公共路由目录。 */
public final class GatewayRuntimeCatalog {
    private final List<GatewayRuntimeRoute> routes;
    private final Map<RouteKey, GatewayRuntimeRoute> routesByEndpoint;
    private final Map<ChannelMethodKey, List<CompiledRoute>> routesByChannelAndMethod;

    public GatewayRuntimeCatalog(List<GatewayRuntimeRoute> routes) {
        this.routes = List.copyOf(routes);
        this.routesByEndpoint = index(this.routes);
        this.routesByChannelAndMethod = compile(this.routes);
    }

    public List<GatewayRuntimeRoute> all() {
        return routes;
    }

    public Optional<GatewayRuntimeRoute> find(GatewayRouteChannel channel, HttpMethod method, String rawPath) {
        return match(channel, method, rawPath).map(GatewayRouteMatch::route);
    }

    public Optional<GatewayRouteMatch> match(GatewayRouteChannel channel, HttpMethod method, String rawPath) {
        if (channel == null || method == null || rawPath == null) {
            return Optional.empty();
        }
        for (CompiledRoute candidate : routesByChannelAndMethod.getOrDefault(new ChannelMethodKey(channel, method), List.of())) {
            Optional<Map<String, String>> variables = candidate.externalPath().match(rawPath);
            if (variables.isPresent()) {
                return Optional
                    .of(new GatewayRouteMatch(candidate.route(), candidate.downstreamPath().render(variables.get())));
            }
        }
        return Optional.empty();
    }

    public List<GatewayRouteMatch> matchesPath(GatewayRouteChannel channel, String rawPath) {
        if (channel == null || rawPath == null) {
            return List.of();
        }
        List<GatewayRouteMatch> matches = new ArrayList<>();
        for (Map.Entry<ChannelMethodKey, List<CompiledRoute>> entry : routesByChannelAndMethod.entrySet()) {
            if (entry.getKey().channel() != channel) {
                continue;
            }
            List<CompiledRoute> candidates = entry.getValue();
            for (CompiledRoute candidate : candidates) {
                Optional<Map<String, String>> variables = candidate.externalPath().match(rawPath);
                if (variables.isPresent()) {
                    matches.add(
                        new GatewayRouteMatch(candidate.route(), candidate.downstreamPath().render(variables.get())));
                    break;
                }
            }
        }
        return List.copyOf(matches);
    }

    private Map<ChannelMethodKey, List<CompiledRoute>> compile(List<GatewayRuntimeRoute> values) {
        Map<ChannelMethodKey, List<CompiledRoute>> compiled = new HashMap<>();
        for (GatewayRuntimeRoute route : values) {
            GatewayPathTemplate external = GatewayPathTemplate.compile(route.externalPath());
            GatewayPathTemplate downstream = GatewayPathTemplate.compile(route.downstreamPath());
            compiled.computeIfAbsent(new ChannelMethodKey(route.channel(), route.method()), ignored -> new ArrayList<>())
                .add(new CompiledRoute(route, external, downstream));
        }
        Comparator<CompiledRoute> priority =
            Comparator.comparingInt((CompiledRoute route) -> route.externalPath().variableCount())
                .thenComparing(route -> route.externalPath().value()).thenComparing(route -> route.route().routeCode());
        compiled.replaceAll((ignored, routes) -> routes.stream().sorted(priority).toList());
        return Map.copyOf(compiled);
    }

    private Map<RouteKey, GatewayRuntimeRoute> index(List<GatewayRuntimeRoute> values) {
        Map<RouteKey, GatewayRuntimeRoute> index = new HashMap<>();
        for (GatewayRuntimeRoute route : values) {
            GatewayRuntimeRoute duplicate =
                index.putIfAbsent(new RouteKey(route.channel(), route.method(), route.externalPath()), route);
            if (duplicate != null) {
                throw new IllegalArgumentException(
                    "公共 Route Channel/Method/Path 重复: " + route.channel().getCode() + " " + route.method() + " "
                        + route.externalPath());
            }
        }
        return Map.copyOf(index);
    }

    private record RouteKey(GatewayRouteChannel channel, HttpMethod method, String rawPath) {
        private RouteKey {
            channel = Objects.requireNonNull(channel, "Route Channel 不可为空");
            method = Objects.requireNonNull(method, "HTTP Method 不可为空");
            rawPath = Objects.requireNonNull(rawPath, "请求路径不可为空");
        }
    }

    private record ChannelMethodKey(GatewayRouteChannel channel, HttpMethod method) {
        private ChannelMethodKey {
            channel = Objects.requireNonNull(channel, "Route Channel 不可为空");
            method = Objects.requireNonNull(method, "HTTP Method 不可为空");
        }
    }

    private record CompiledRoute(GatewayRuntimeRoute route, GatewayPathTemplate externalPath,
        GatewayPathTemplate downstreamPath) {
    }
}

package com.paytech.gateway.routing.runtime;

import java.util.Objects;

/** 单次请求从固定 Snapshot 得到的 Route 与安全重建后的下游路径。 */
public record GatewayRouteMatch(GatewayRuntimeRoute route, String downstreamPath) {
    public GatewayRouteMatch {
        route = Objects.requireNonNull(route, "运行时 Route 不可为空");
        if (downstreamPath == null || !downstreamPath.startsWith("/")) {
            throw new IllegalArgumentException("下游路径不可为空");
        }
    }
}

package com.paytech.gateway.routing.runtime;

import java.net.URI;
import java.util.regex.Pattern;

import com.paytech.platform.support.api.enums.GatewayRouteChannel;

/** 来自完整快照的目标身份；服务名只用于现有服务发现，不接受直接网络地址。 */
public record GatewayRuntimeTarget(String targetServiceCode, GatewayRouteChannel channel, String discoveryServiceName) {
    private static final Pattern SERVICE_NAME = Pattern.compile(
        "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*");

    /** 固定已校验身份，服务名规则与 Platform 控制面一致。 */
    public GatewayRuntimeTarget {
        if (targetServiceCode == null || !targetServiceCode.matches("[a-z][a-z0-9-]{2,127}") || channel == null
            || discoveryServiceName == null || discoveryServiceName.length() > 255
            || !SERVICE_NAME.matcher(discoveryServiceName).matches()) {
            throw new IllegalArgumentException("Gateway Target 身份或服务发现名称非法");
        }
    }

    /** 派生仅包含服务发现名称的负载均衡 URI，不使用请求 Host、Header 或 Query。 */
    public URI routingUri() {
        return URI.create("lb://" + discoveryServiceName);
    }
}

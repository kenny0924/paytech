package com.paytech.gateway.web;

import java.util.Optional;

import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.security.identity.GatewayAuthenticatedIdentity;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

/** Gateway 请求上下文属性名称。 */
public final class GatewayExchangeAttributes {
    public static final String REQUEST_ID = GatewayExchangeAttributes.class.getName() + ".requestId";
    public static final String ENTRY_CHANNEL = GatewayExchangeAttributes.class.getName() + ".entryChannel";
    public static final String CLIENT_ADDRESS = GatewayExchangeAttributes.class.getName() + ".clientAddress";
    public static final String ROUTE_CODE = GatewayExchangeAttributes.class.getName() + ".routeCode";
    public static final String RUNTIME_ROUTE = GatewayExchangeAttributes.class.getName() + ".runtimeRoute";
    public static final String DOWNSTREAM_PATH = GatewayExchangeAttributes.class.getName() + ".downstreamPath";
    public static final String OUTCOME = GatewayExchangeAttributes.class.getName() + ".outcome";
    public static final String ACCESS_SUBJECT_DIGEST =
        GatewayExchangeAttributes.class.getName() + ".accessSubjectDigest";
    public static final String ACCESS_CLIENT_DIGEST =
        GatewayExchangeAttributes.class.getName() + ".accessClientDigest";
    public static final String AUTHENTICATED_IDENTITY =
        GatewayExchangeAttributes.class.getName() + ".authenticatedIdentity";
    public static final String TRUSTED_CONSOLE_ORIGIN =
        GatewayExchangeAttributes.class.getName() + ".trustedConsoleOrigin";
    public static final String INGRESS_GUARD_LIFECYCLE =
        GatewayExchangeAttributes.class.getName() + ".ingressGuardLifecycle";
    public static final String AUTHENTICATED_GUARD_LIFECYCLE =
        GatewayExchangeAttributes.class.getName() + ".authenticatedGuardLifecycle";
    public static final String ACTUAL_REQUEST_BODY_LENGTH =
        GatewayExchangeAttributes.class.getName() + ".actualRequestBodyLength";
    public static final String UPSTREAM_DISPATCH_STARTED_AT_NANOS =
        GatewayExchangeAttributes.class.getName() + ".upstreamDispatchStartedAtNanos";
    public static final String REPLAYABLE_REQUEST_BODY_OWNER =
        GatewayExchangeAttributes.class.getName() + ".replayableRequestBodyOwner";

    private GatewayExchangeAttributes() {}

    public static Optional<GatewayRouteChannel> entryChannel(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(ENTRY_CHANNEL);
        return value instanceof GatewayRouteChannel channel ? Optional.of(channel) : Optional.empty();
    }

    public static Optional<GatewayRuntimeRoute> runtimeRoute(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(RUNTIME_ROUTE);
        return value instanceof GatewayRuntimeRoute route ? Optional.of(route) : Optional.empty();
    }

    public static Optional<GatewayAuthenticatedIdentity> authenticatedIdentity(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(AUTHENTICATED_IDENTITY);
        return value instanceof GatewayAuthenticatedIdentity identity ? Optional.of(identity) : Optional.empty();
    }
}

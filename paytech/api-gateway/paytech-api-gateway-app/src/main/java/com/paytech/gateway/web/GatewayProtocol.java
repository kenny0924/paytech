package com.paytech.gateway.web;

import java.util.Locale;
import java.util.Set;

import com.paytech.foundation.common.http.ApiHeaderNames;

/** 公共 Gateway 入口及可信下游上下文的固定协议名称。 */
public final class GatewayProtocol {
    public static final String REQUEST_ID = ApiHeaderNames.REQUEST_ID;
    public static final String ENTRY_CHANNEL = "X-Entry-Channel";
    public static final String CLIENT_ID = ApiHeaderNames.GATEWAY_CLIENT_ID;
    public static final String CREDENTIAL_ID = ApiHeaderNames.GATEWAY_CREDENTIAL_ID;
    public static final String ROUTE_CODE = "X-Gateway-Route-Code";
    public static final String CLIENT_IP = "X-Real-IP";
    public static final String ACTOR_SUBJECT = "X-Gateway-Actor-Subject";
    public static final String SESSION_ID = "X-Gateway-Session-Id";
    public static final String ACTOR_TYPE = "X-Gateway-Actor-Type";
    public static final String CHANNEL = "X-Gateway-Channel";
    public static final String REALM = "X-Gateway-Realm";
    public static final String CONTEXT_TYPE = "X-Gateway-Context-Type";
    public static final String TENANT_ID = "X-Gateway-Tenant-Id";
    public static final String TENANT_TYPE = "X-Gateway-Tenant-Type";
    public static final String TENANT_MEMBER_ID = "X-Gateway-Tenant-Member-Id";
    public static final String CSRF_TOKEN = "X-CSRF-Token";
    public static final String REFRESH_COOKIE = "__Secure-operations-refresh";
    public static final String TEST_REFRESH_COOKIE = "operations-refresh-test";
    public static final String EXPOSE_HEADERS = REQUEST_ID + ", Retry-After";

    private static final Set<String> RESERVED_INGRESS_HEADERS = Set.of("x-entry-channel", "x-request-id", "x-gateway-route-code",
        "x-gateway-actor-subject", "x-gateway-session-id", "x-gateway-actor-type", "x-gateway-channel",
        "x-gateway-realm", "x-gateway-context-type", "x-gateway-tenant-id", "x-gateway-tenant-type",
        "x-gateway-tenant-member-id", "forwarded", "x-forwarded-for", "x-forwarded-host", "x-forwarded-port",
        "x-forwarded-prefix", "x-forwarded-proto", "x-forwarded-server", "x-forwarded-ssl", "x-real-ip");

    private GatewayProtocol() {}

    public static boolean isReservedIngressHeader(String headerName) {
        String normalizedName = headerName.toLowerCase(Locale.ROOT);
        return normalizedName.startsWith("x-gateway-") || RESERVED_INGRESS_HEADERS.contains(normalizedName);
    }
}

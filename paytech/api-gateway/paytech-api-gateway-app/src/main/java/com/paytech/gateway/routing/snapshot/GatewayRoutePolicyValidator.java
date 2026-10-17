package com.paytech.gateway.routing.snapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.paytech.platform.support.api.enums.*;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayRouteSnapshot;

/** 校验平台下发路由中由 Gateway 固化执行的安全契约与绝对上限。 */
final class GatewayRoutePolicyValidator {
    private static final long MAX_BODY_BYTES = 65_536;
    private static final int MAX_BODY_RECEIVE_TIMEOUT_MILLIS = 5_000;
    private static final int MAX_RESPONSE_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_CORS_HEADERS = 64;
    private static final int MAX_FORWARDED_HEADERS = 64;
    private static final int MAX_QUERY_PARAMETERS = 32;
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'+.^_`|~0-9A-Za-z-]{1,255}");
    private static final Pattern PERMISSION_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");
    private static final Pattern SCOPE_CODE = Pattern.compile("[a-z][a-z0-9]*(?:[.:-][a-z0-9]+)*");
    private static final Set<String> FORBIDDEN_HEADERS = Set.of("connection", "keep-alive", "proxy-authenticate",
        "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length", "cookie",
        "set-cookie", "forwarded", "x-forwarded-for", "x-forwarded-host", "x-forwarded-port", "x-forwarded-proto",
        "x-real-ip", "x-request-id", "x-gateway-route-code", "x-gateway-actor-subject", "x-gateway-session-id",
        "x-gateway-actor-type", "x-gateway-channel", "x-gateway-tenant-id", "x-gateway-tenant-type", "x-gateway-realm",
        "x-gateway-context-type", "x-gateway-tenant-member-id", "x-gateway-owner-ref-id", "x-gateway-credential-id");

    void validate(GatewayRouteSnapshot route) {
        validateCompatibility(route);
        validateHeaders(route.getCorsRequestHeaderNames(), false);
        validateHeaders(route.getForwardedRequestHeaderNames(), true);
        if ("CLIENT_API".equals(route.getChannel()) && !route.getCorsRequestHeaderNames().isEmpty()) {
            throw invalid("Client API 不允许 CORS Header 配置");
        }
        validateQueries(route.getQueryParameterNames());
        validateLimits(route);
        validateIdempotency(route);
    }

    /** REQUIRED 仅用于 Client API 写路由，且 Key 由 Gateway 消费、不再转发给 Owner。 */
    private void validateIdempotency(GatewayRouteSnapshot route) {
        if (GatewaySnapshotCodeParser.idempotencyMode(route.getIdempotencyMode()) != GatewayIdempotencyMode.REQUIRED) {
            return;
        }
        GatewayHttpMethod method = GatewaySnapshotCodeParser.httpMethod(route.getHttpMethod());
        if (!"CLIENT_API".equals(route.getChannel()) || method == GatewayHttpMethod.GET
            || method == GatewayHttpMethod.HEAD) {
            throw invalid("Gateway Route 请求幂等仅允许 Client API 写方法: " + route.getRouteCode());
        }
        if (route.getForwardedRequestHeaderNames().stream().anyMatch(IDEMPOTENCY_KEY_HEADER::equalsIgnoreCase)) {
            throw invalid("Gateway Route 请求幂等不得转发 Idempotency-Key: " + route.getRouteCode());
        }
    }

    private void validateCompatibility(GatewayRouteSnapshot route) {
        GatewayRouteChannel channel = GatewaySnapshotCodeParser.routeChannel(route.getChannel());
        GatewaySecurityProfileCode profile = GatewaySnapshotCodeParser.securityProfile(route.getSecurityProfileCode());
        GatewayAuthorizationMode mode = GatewaySnapshotCodeParser.authorizationMode(route.getAuthorizationMode());
        GatewayRequestCookieMode requestCookie = GatewaySnapshotCodeParser.requestCookieMode(route.getRequestCookieMode());
        GatewayResponseCookieMode responseCookie =
            GatewaySnapshotCodeParser.responseCookieMode(route.getResponseCookieMode());
        if (!validProfile(channel, profile) || !validMode(channel, mode) || !validProfileMode(profile, mode)
            || !validCookieModes(channel, requestCookie, responseCookie) || !validBinding(route, profile, mode)) {
            throw invalid("Gateway Route 安全兼容性非法: " + route.getRouteCode());
        }
    }

    private boolean validProfile(GatewayRouteChannel channel, GatewaySecurityProfileCode profile) {
        return switch (channel) {
            case OPERATIONS ->
                Set.of(GatewaySecurityProfileCode.PUBLIC, GatewaySecurityProfileCode.OPERATIONS_JWT).contains(profile);
            case CLIENT ->
                Set.of(GatewaySecurityProfileCode.PUBLIC, GatewaySecurityProfileCode.CLIENT_JWT).contains(profile);
            case PARTNER ->
                Set.of(GatewaySecurityProfileCode.PUBLIC, GatewaySecurityProfileCode.PARTNER_JWT).contains(profile);
            case CLIENT_API -> profile == GatewaySecurityProfileCode.CLIENT_API_CREDENTIAL;
            case CHECKOUT -> profile == GatewaySecurityProfileCode.CHECKOUT_SESSION;
            case CHANNEL_CALLBACK -> profile == GatewaySecurityProfileCode.CHANNEL_CALLBACK;
        };
    }

    private boolean validMode(GatewayRouteChannel channel, GatewayAuthorizationMode mode) {
        return switch (channel) {
            case OPERATIONS, CLIENT, PARTNER -> Set.of(GatewayAuthorizationMode.PUBLIC,
                GatewayAuthorizationMode.HUMAN_AUTHENTICATED, GatewayAuthorizationMode.HUMAN_PERMISSION).contains(mode);
            case CLIENT_API -> mode == GatewayAuthorizationMode.API_SCOPE;
            case CHECKOUT -> mode == GatewayAuthorizationMode.CHECKOUT_RESOURCE;
            case CHANNEL_CALLBACK -> mode == GatewayAuthorizationMode.CHANNEL_CALLBACK;
        };
    }

    private boolean validProfileMode(GatewaySecurityProfileCode profile, GatewayAuthorizationMode mode) {
        if (profile == GatewaySecurityProfileCode.PUBLIC) {
            return mode == GatewayAuthorizationMode.PUBLIC;
        }
        if (Set.of(GatewaySecurityProfileCode.OPERATIONS_JWT, GatewaySecurityProfileCode.CLIENT_JWT,
            GatewaySecurityProfileCode.PARTNER_JWT).contains(profile)) {
            return Set.of(GatewayAuthorizationMode.HUMAN_AUTHENTICATED, GatewayAuthorizationMode.HUMAN_PERMISSION)
                .contains(mode);
        }
        return switch (profile) {
            case CLIENT_API_CREDENTIAL -> mode == GatewayAuthorizationMode.API_SCOPE;
            case CHECKOUT_SESSION -> mode == GatewayAuthorizationMode.CHECKOUT_RESOURCE;
            case CHANNEL_CALLBACK -> mode == GatewayAuthorizationMode.CHANNEL_CALLBACK;
            default -> false;
        };
    }

    private boolean validCookieModes(GatewayRouteChannel channel, GatewayRequestCookieMode requestCookie,
        GatewayResponseCookieMode responseCookie) {
        boolean humanChannel =
            Set.of(GatewayRouteChannel.OPERATIONS, GatewayRouteChannel.CLIENT, GatewayRouteChannel.PARTNER)
                .contains(channel);
        return humanChannel
            || requestCookie == GatewayRequestCookieMode.DROP && responseCookie == GatewayResponseCookieMode.NONE;
    }

    private boolean validBinding(GatewayRouteSnapshot route, GatewaySecurityProfileCode profile,
        GatewayAuthorizationMode mode) {
        boolean permission = mode == GatewayAuthorizationMode.HUMAN_PERMISSION;
        boolean scope = mode == GatewayAuthorizationMode.API_SCOPE;
        return permission == present(route.getPermissionCode()) && scope == present(route.getRequiredScopeCode())
            && (!permission || PERMISSION_CODE.matcher(route.getPermissionCode()).matches())
            && (!scope
                || route.getRequiredScopeCode().length() <= 128 && SCOPE_CODE.matcher(route.getRequiredScopeCode()).matches())
            && !(profile == GatewaySecurityProfileCode.PUBLIC && mode != GatewayAuthorizationMode.PUBLIC);
    }

    private void validateHeaders(List<String> values, boolean forwarded) {
        int maximum = forwarded ? MAX_FORWARDED_HEADERS : MAX_CORS_HEADERS;
        if (values == null || values.size() > maximum
            || values.stream().anyMatch(value -> !validHeader(value, forwarded))
            || values.stream().map(value -> value.toLowerCase(Locale.ROOT)).distinct().count() != values.size()) {
            throw invalid("Gateway Header 白名单非法");
        }
    }

    private boolean validHeader(String value, boolean forwarded) {
        if (value == null || !HEADER_NAME.matcher(value).matches()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return !FORBIDDEN_HEADERS.contains(normalized) && !normalized.startsWith("x-forwarded-")
            && !normalized.startsWith("x-gateway-") && !"x-entry-channel".equals(normalized)
            && !"x-platform-api-credential".equals(normalized)
            && !(forwarded && "authorization".equals(normalized));
    }

    private void validateQueries(List<String> values) {
        if (values == null || values.size() > MAX_QUERY_PARAMETERS || new HashSet<>(values).size() != values.size()
            || values.stream().anyMatch(value -> value == null || !value.matches("[A-Za-z][A-Za-z0-9_.-]{0,254}"))) {
            throw invalid("Gateway Query 白名单非法");
        }
    }

    private void validateLimits(GatewayRouteSnapshot route) {
        if (route.getBodyLimitBytes() < 0 || route.getBodyLimitBytes() > MAX_BODY_BYTES
            || route.getBodyReceiveTimeoutMillis() <= 0 || route.getResponseTimeoutMillis() <= 0
            || route.getBodyReceiveTimeoutMillis() > MAX_BODY_RECEIVE_TIMEOUT_MILLIS
            || route.getResponseTimeoutMillis() > MAX_RESPONSE_TIMEOUT_MILLIS) {
            throw invalid("Gateway Body 或 Timeout 超过静态上限");
        }
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private InvalidGatewaySnapshotException invalid(String message) {
        return new InvalidGatewaySnapshotException(message);
    }
}

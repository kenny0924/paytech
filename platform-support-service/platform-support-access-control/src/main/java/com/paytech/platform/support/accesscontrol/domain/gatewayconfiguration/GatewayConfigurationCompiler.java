package com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration;

import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCapabilities.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.platform.support.api.enums.GatewayAuthorizationMode;
import com.paytech.platform.support.api.enums.GatewayGuardDimension;
import com.paytech.platform.support.api.enums.GatewayGuardRuleType;
import com.paytech.platform.support.api.enums.GatewayRequestCookieMode;
import com.paytech.platform.support.api.enums.GatewayResponseCookieMode;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.api.enums.GatewaySecurityProfileCode;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewaySnapshotView.GuardRule;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewaySnapshotView.Route;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewaySnapshotView.Target;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.GuardRuleRow;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.HeaderRow;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.LoadedConfiguration;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.PermissionRow;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.QueryParameterRow;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.RouteRow;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.TargetRow;

/**
 * 将一个脱离事务的数据库投影编译为完整、不可变且可安全发布的 Gateway 当前配置。
 */
@Component
public final class GatewayConfigurationCompiler {

    private static final Pattern STABLE_CODE = Pattern.compile("[a-z][a-z0-9-]{2,127}");
    private static final Pattern DISCOVERY_SERVICE =
        Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*");
    private static final Pattern PERMISSION_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");
    private static final Pattern SCOPE_CODE = Pattern.compile("[a-z][a-z0-9]*(?:[.:-][a-z0-9]+)*");
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final Pattern FIXED_PATH_SEGMENT = Pattern.compile("(?:[A-Za-z0-9._~!$&'+,;=:@-]|%[0-9A-Fa-f]{2})+");
    private static final Pattern QUERY_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,254}");
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'+.^_`|~0-9A-Za-z-]+");

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");
    private static final Set<String> HEADER_USES = Set.of("CORS_REQUEST_ALLOWED", "FORWARD_REQUEST");
    private static final Set<String> FORBIDDEN_HEADERS =
        Set.of("connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailer",
            "transfer-encoding", "upgrade", "host", "content-length", "cookie", "set-cookie", "forwarded",
            "x-forwarded-for", "x-forwarded-host", "x-forwarded-port", "x-forwarded-proto", "x-real-ip", "x-request-id",
            "x-gateway-route-code", "x-gateway-actor-subject", "x-gateway-session-id", "x-gateway-actor-type",
            "x-gateway-channel", "x-gateway-tenant-id", "x-gateway-tenant-type", "x-gateway-owner-ref-id",
            "x-gateway-credential-id", "x-gateway-realm", "x-gateway-context-type", "x-gateway-tenant-member-id");

    private static final long MAX_BODY_LIMIT_BYTES = 65_536L;
    private static final int MAX_BODY_RECEIVE_TIMEOUT_MILLIS = 5_000;
    private static final int MAX_RESPONSE_TIMEOUT_MILLIS = 10_000;
    private static final long MAX_RATE_CAPACITY = 600L;
    private static final long MAX_RATE_REFILL_TOKENS = 600L;
    private static final long MAX_RATE_REFILL_PERIOD_MILLIS = 60_000L;
    private static final long MAX_CONCURRENCY_LIMIT = 50L;
    private static final int MAX_TARGETS = 128;
    private static final int MAX_ROUTES = 4_096;
    private static final int MAX_CORS_HEADERS = 64;
    private static final int MAX_FORWARDED_HEADERS = 64;
    private static final int MAX_QUERY_PARAMETERS = 32;
    private static final int MAX_GUARD_RULES = 8;
    private static final int BITSET_INDEX_THRESHOLD = 64;
    private static final int DENSE_VALUE_INDEX_THRESHOLD = 64;
    private static final int NO_FIXED_POSITION = -1;
    private static final int NO_COMPATIBLE_ROUTE = -2;

    private static final Comparator<String> BINARY_STRING = GatewayConfigurationCompiler::compareBinary;
    private static final Comparator<Target> TARGET_ORDER = Comparator
        .comparing(Target::targetServiceCode, BINARY_STRING).thenComparing(target -> target.channel().getCode());
    private static final Comparator<Route> ROUTE_ORDER = Comparator.comparing(Route::routeCode, BINARY_STRING);
    private static final Comparator<GuardRule> GUARD_ORDER = Comparator
        .comparing((GuardRule rule) -> rule.ruleType().getCode()).thenComparing(rule -> rule.dimension().getCode());

    private static int compareBinary(String first, String second) {
        byte[] left = first.getBytes(StandardCharsets.UTF_8);
        byte[] right = second.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(left.length, right.length);
        for (int index = 0; index < length; index++) {
            int comparison = Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    /**
     * 编译当前配置。任何歧义或损坏都会拒绝整份结果。
     *
     * @param loaded
     *            事务内捕获、事务外使用的不可变投影
     * @return 完整有效配置
     */
    public GatewaySnapshotView compile(LoadedConfiguration loaded) {
        Objects.requireNonNull(loaded, "loaded");
        requireMaximum("Gateway Target", loaded.targets().size(), MAX_TARGETS);
        requireMaximum("Gateway Route", loaded.routes().size(), MAX_ROUTES);
        if (loaded.targets().isEmpty()) {
            throw invalid("Gateway Catalog 尚未初始化：不存在 Target");
        }

        TargetIndex targetIndex = indexTargets(loaded.targets());
        RouteIndex routeIndex = indexRoutes(loaded.routes(), targetIndex.byId());
        ChildIndex children = indexChildren(loaded, routeIndex.byId());
        Map<Long, String> permissions = indexPermissions(loaded.permissions());

        List<Route> effectiveRoutes = new ArrayList<>();
        for (RouteState state : routeIndex.states()) {
            Route compiled = compileRoute(state, permissions, children);
            if (state.effective()) {
                effectiveRoutes.add(compiled);
            }
        }
        rejectOverlappingRoutes(effectiveRoutes);

        List<Target> activeTargets = targetIndex.states().stream().filter(TargetState::active).map(TargetState::target)
            .sorted(TARGET_ORDER).toList();
        effectiveRoutes.sort(ROUTE_ORDER);
        return new GatewaySnapshotView(activeTargets, effectiveRoutes);
    }

    private TargetIndex indexTargets(List<TargetRow> rows) {
        Map<Long, TargetState> byId = new LinkedHashMap<>();
        Set<String> codes = new HashSet<>();
        for (TargetRow row : rows) {
            requirePositiveId("Target ID", row.targetId());
            requireStableCode("Target Service Code", row.targetServiceCode(), 128);
            requireDiscoveryService(row.discoveryServiceName());
            GatewayRouteChannel channel = parseCode(GatewayRouteChannel.class, row.channel(), "Target Channel");
            boolean active = parseStatus(row.status(), "Target", row.targetServiceCode());
            if (byId.containsKey(row.targetId())) {
                throw invalid("Target ID 重复: " + row.targetId());
            }
            if (!codes.add(row.targetServiceCode())) {
                throw invalid("Target Service Code 重复: " + row.targetServiceCode());
            }
            Target target = new Target(row.targetServiceCode(), channel, row.discoveryServiceName());
            byId.put(row.targetId(), new TargetState(row.targetId(), target, active));
        }
        return new TargetIndex(Map.copyOf(byId), List.copyOf(byId.values()));
    }

    private RouteIndex indexRoutes(List<RouteRow> rows, Map<Long, TargetState> targets) {
        Map<Long, RouteState> byId = new LinkedHashMap<>();
        Set<String> codes = new HashSet<>();
        for (RouteRow row : rows) {
            requirePositiveId("Route ID", row.routeId());
            requireStableCode("Route Code", row.routeCode(), 128);
            boolean active = parseStatus(row.status(), "Route", row.routeCode());
            TargetState target = targets.get(row.targetId());
            if (target == null) {
                throw invalid("Route 引用了不存在的 Target: route=" + row.routeCode());
            }
            GatewayRouteChannel channel = parseCode(GatewayRouteChannel.class, row.channel(), "Route Channel");
            if (channel != target.target().channel()) {
                throw invalid("Route 与 Target Channel 不一致: route=" + row.routeCode());
            }
            if (byId.containsKey(row.routeId())) {
                throw invalid("Route ID 重复: " + row.routeId());
            }
            if (!codes.add(row.routeCode())) {
                throw invalid("Route Code 重复: " + row.routeCode());
            }
            byId.put(row.routeId(), new RouteState(row, target, channel, active && target.active()));
        }
        return new RouteIndex(Map.copyOf(byId), List.copyOf(byId.values()));
    }

    private ChildIndex indexChildren(LoadedConfiguration loaded, Map<Long, RouteState> routes) {
        Map<Long, List<HeaderRow>> headers = groupChildren(loaded.headers(), HeaderRow::routeId, routes, "Header");
        Map<Long, List<QueryParameterRow>> queries =
            groupChildren(loaded.queryParameters(), QueryParameterRow::routeId, routes, "Query Parameter");
        Map<Long, List<GuardRuleRow>> guards =
            groupChildren(loaded.guardRules(), GuardRuleRow::routeId, routes, "Guard Rule");
        return new ChildIndex(headers, queries, guards);
    }

    private <T> Map<Long, List<T>> groupChildren(List<T> rows, Function<T, Long> routeId, Map<Long, RouteState> routes,
        String childName) {
        Map<Long, List<T>> grouped = new HashMap<>();
        for (T row : rows) {
            Long ownerId = routeId.apply(row);
            if (ownerId == null || !routes.containsKey(ownerId)) {
                throw invalid(childName + " 引用了不存在的 Route: routeId=" + ownerId);
            }
            grouped.computeIfAbsent(ownerId, ignored -> new ArrayList<>()).add(row);
        }
        Map<Long, List<T>> immutable = new HashMap<>();
        grouped.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        return Map.copyOf(immutable);
    }

    private Map<Long, String> indexPermissions(List<PermissionRow> rows) {
        Map<Long, String> byId = new HashMap<>();
        Set<String> codes = new HashSet<>();
        for (PermissionRow row : rows) {
            requirePositiveId("Permission ID", row.permissionId());
            requirePattern("Permission Code", row.permissionCode(), 128, PERMISSION_CODE);
            if (byId.putIfAbsent(row.permissionId(), row.permissionCode()) != null) {
                throw invalid("Permission ID 存在歧义: " + row.permissionId());
            }
            if (!codes.add(row.permissionCode())) {
                throw invalid("Permission Code 存在歧义: " + row.permissionCode());
            }
        }
        return Map.copyOf(byId);
    }

    private Route compileRoute(RouteState state, Map<Long, String> permissions, ChildIndex children) {
        RouteRow row = state.row();
        validateTransport(row);
        PathTemplate externalPath = parsePath(row.externalPathTemplate(), "External Path", row.routeCode());
        PathTemplate downstreamPath = parsePath(row.downstreamPathTemplate(), "Downstream Path", row.routeCode());
        if (!externalPath.variableNames().equals(downstreamPath.variableNames())) {
            throw invalid("Route 外部与下游 Path Variable 集合不一致: " + row.routeCode());
        }

        GatewaySecurityProfileCode profile =
            parseCode(GatewaySecurityProfileCode.class, row.securityProfileCode(), "Security Profile");
        GatewayAuthorizationMode mode =
            parseCode(GatewayAuthorizationMode.class, row.authorizationMode(), "Authorization Mode");
        GatewayRequestCookieMode requestCookie =
            parseCode(GatewayRequestCookieMode.class, row.requestCookieMode(), "Request Cookie Mode");
        GatewayResponseCookieMode responseCookie =
            parseCode(GatewayResponseCookieMode.class, row.responseCookieMode(), "Response Cookie Mode");
        validateCompatibility(state.channel(), profile, mode, requestCookie, responseCookie, row.routeCode());
        PermissionBinding binding = resolveAuthorization(row, mode, permissions);

        List<HeaderRow> headerRows = children.headers().getOrDefault(row.routeId(), List.of());
        HeaderSets headerSets = compileHeaders(headerRows, row.routeCode());
        List<String> queryNames =
            compileQueries(children.queries().getOrDefault(row.routeId(), List.of()), row.routeCode());
        List<GuardRule> guardRules =
            compileGuards(children.guards().getOrDefault(row.routeId(), List.of()), profile, row.routeCode());

        return new Route(row.routeCode(), state.channel(), row.httpMethod(), row.externalPathTemplate(),
            row.downstreamPathTemplate(), state.target().target().targetServiceCode(), profile, mode,
            binding.permissionCode(), binding.scopeCode(), requestCookie, responseCookie, headerSets.cors(),
            headerSets.forwarded(), queryNames, row.bodyLimitBytes(), row.bodyReceiveTimeoutMillis(),
            row.responseTimeoutMillis(), guardRules);
    }

    private void validateTransport(RouteRow row) {
        if (!METHODS.contains(row.httpMethod())) {
            throw invalid("HTTP Method 不受支持或不是大写精确值: route=" + row.routeCode());
        }
        if (row.bodyLimitBytes() < 0L || row.bodyLimitBytes() > MAX_BODY_LIMIT_BYTES) {
            throw invalid("Body Limit 超出 Gateway 绝对上限: route=" + row.routeCode());
        }
        if (row.bodyReceiveTimeoutMillis() <= 0 || row.bodyReceiveTimeoutMillis() > MAX_BODY_RECEIVE_TIMEOUT_MILLIS
            || row.responseTimeoutMillis() <= 0 || row.responseTimeoutMillis() > MAX_RESPONSE_TIMEOUT_MILLIS) {
            throw invalid("Route Timeout 超出 Gateway 绝对上限: route=" + row.routeCode());
        }
    }

    private void validateCompatibility(GatewayRouteChannel channel, GatewaySecurityProfileCode profile,
        GatewayAuthorizationMode mode, GatewayRequestCookieMode requestCookie, GatewayResponseCookieMode responseCookie,
        String routeCode) {
        boolean compatible = compatible(channel, profile, mode);
        if (!compatible) {
            throw invalid("Channel/Profile/Authorization Mode 不兼容: route=" + routeCode);
        }
        boolean humanChannel = humanChannel(channel);
        if (!humanChannel
            && (requestCookie != GatewayRequestCookieMode.DROP || responseCookie != GatewayResponseCookieMode.NONE)) {
            throw invalid("非人工 Channel 只能使用 DROP/NONE Cookie Mode: route=" + routeCode);
        }
    }

    /** 配置选项与完整候选校验使用同一兼容性谓词。 */
    private boolean compatible(GatewayRouteChannel channel, GatewaySecurityProfileCode profile,
        GatewayAuthorizationMode mode) {
        return switch (channel) {
            case OPERATIONS -> humanCompatibility(profile, mode, GatewaySecurityProfileCode.OPERATIONS_JWT);
            case CLIENT -> humanCompatibility(profile, mode, GatewaySecurityProfileCode.CLIENT_JWT);
            case PARTNER -> humanCompatibility(profile, mode, GatewaySecurityProfileCode.PARTNER_JWT);
            case CLIENT_API -> profile == GatewaySecurityProfileCode.CLIENT_API_CREDENTIAL
                && mode == GatewayAuthorizationMode.API_SCOPE;
            case CHECKOUT -> profile == GatewaySecurityProfileCode.CHECKOUT_SESSION
                && mode == GatewayAuthorizationMode.CHECKOUT_RESOURCE;
            case CHANNEL_CALLBACK -> profile == GatewaySecurityProfileCode.CHANNEL_CALLBACK
                && mode == GatewayAuthorizationMode.CHANNEL_CALLBACK;
        };
    }

    /** 只有人工入口支持已有 Cookie 模式；不新增认证实现。 */
    private boolean humanChannel(GatewayRouteChannel channel) {
        return EnumSet.of(GatewayRouteChannel.OPERATIONS, GatewayRouteChannel.CLIENT, GatewayRouteChannel.PARTNER)
            .contains(channel);
    }

    /** 对管理表单公开编译器能力，不允许 Web 另写合法组合和上限。 */
    public Capabilities capabilities() {
        List<ChannelCapability> channels = java.util.Arrays.stream(GatewayRouteChannel.values()).map(channel -> {
            List<ProfileCapability> profiles = java.util.Arrays.stream(GatewaySecurityProfileCode.values())
                .map(profile -> new ProfileCapability(profile.getCode(),
                    java.util.Arrays.stream(GatewayAuthorizationMode.values())
                        .filter(mode -> compatible(channel, profile, mode)).map(GatewayAuthorizationMode::getCode).toList(),
                    java.util.Arrays.stream(GatewayRequestCookieMode.values())
                        .filter(mode -> humanChannel(channel) || mode == GatewayRequestCookieMode.DROP)
                        .map(GatewayRequestCookieMode::getCode).toList(),
                    java.util.Arrays.stream(GatewayResponseCookieMode.values())
                        .filter(mode -> humanChannel(channel) || mode == GatewayResponseCookieMode.NONE)
                        .map(GatewayResponseCookieMode::getCode).toList(),
                    java.util.Arrays.stream(GatewayGuardDimension.values()).filter(d -> guardDimensionAllowed(profile, d))
                        .map(GatewayGuardDimension::getCode).toList(),
                    requiredGuardDimensions(profile).stream().map(GatewayGuardDimension::getCode).toList()))
                .filter(profile -> !profile.authorizationModes().isEmpty()).toList();
            return new ChannelCapability(channel.getCode(), profiles);
        }).toList();
        return new Capabilities(channels, METHODS.stream().sorted().toList(),
            new Limits(MAX_BODY_LIMIT_BYTES, MAX_BODY_RECEIVE_TIMEOUT_MILLIS, MAX_RESPONSE_TIMEOUT_MILLIS,
                MAX_RATE_CAPACITY, MAX_RATE_REFILL_TOKENS, MAX_RATE_REFILL_PERIOD_MILLIS, MAX_CONCURRENCY_LIMIT,
                MAX_TARGETS, MAX_ROUTES, MAX_CORS_HEADERS, MAX_FORWARDED_HEADERS, MAX_QUERY_PARAMETERS, MAX_GUARD_RULES),
            FORBIDDEN_HEADERS.stream().sorted().toList(), HEADER_USES.stream().sorted().toList());
    }

    /** 必需 Guard 维度同时服务编译和表单提示。 */
    private List<GatewayGuardDimension> requiredGuardDimensions(GatewaySecurityProfileCode profile) {
        return profile == GatewaySecurityProfileCode.OPERATIONS_JWT
            ? List.of(GatewayGuardDimension.SHARED_ROUTE, GatewayGuardDimension.CLIENT_IP,
                GatewayGuardDimension.AUTHENTICATED_PRINCIPAL) : List.of(GatewayGuardDimension.SHARED_ROUTE);
    }

    /** 无可信主体的 Profile 不能配置 Principal 或 Tenant 维度。 */
    private boolean guardDimensionAllowed(GatewaySecurityProfileCode profile, GatewayGuardDimension dimension) {
        return EnumSet.of(GatewayGuardDimension.SHARED_ROUTE, GatewayGuardDimension.CLIENT_IP).contains(dimension)
            || profile != GatewaySecurityProfileCode.PUBLIC && profile != GatewaySecurityProfileCode.CHANNEL_CALLBACK;
    }

    private boolean humanCompatibility(GatewaySecurityProfileCode profile, GatewayAuthorizationMode mode,
        GatewaySecurityProfileCode jwtProfile) {
        if (profile == GatewaySecurityProfileCode.PUBLIC) {
            return mode == GatewayAuthorizationMode.PUBLIC;
        }
        return profile == jwtProfile && EnumSet
            .of(GatewayAuthorizationMode.HUMAN_AUTHENTICATED, GatewayAuthorizationMode.HUMAN_PERMISSION).contains(mode);
    }

    private PermissionBinding resolveAuthorization(RouteRow row, GatewayAuthorizationMode mode,
        Map<Long, String> permissions) {
        if (mode == GatewayAuthorizationMode.HUMAN_PERMISSION) {
            if (row.permissionId() == null || row.requiredScopeCode() != null) {
                throw invalid("HUMAN_PERMISSION 必须且只能绑定 Permission: route=" + row.routeCode());
            }
            String code = permissions.get(row.permissionId());
            if (code == null) {
                throw invalid("Route 绑定的 Permission 不存在或存在歧义: route=" + row.routeCode());
            }
            return new PermissionBinding(code, null);
        }
        if (mode == GatewayAuthorizationMode.API_SCOPE) {
            if (row.permissionId() != null) {
                throw invalid("API_SCOPE 不得绑定 Permission: route=" + row.routeCode());
            }
            requirePattern("Required Scope Code", row.requiredScopeCode(), 128, SCOPE_CODE);
            return new PermissionBinding(null, row.requiredScopeCode());
        }
        if (row.permissionId() != null || row.requiredScopeCode() != null) {
            throw invalid("当前 Authorization Mode 不得绑定 Permission/Scope: route=" + row.routeCode());
        }
        return new PermissionBinding(null, null);
    }

    private HeaderSets compileHeaders(List<HeaderRow> rows, String routeCode) {
        List<String> cors = new ArrayList<>();
        List<String> forwarded = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (HeaderRow row : rows) {
            if (!HEADER_USES.contains(row.headerUse())) {
                throw invalid("Header Use 不合法: route=" + routeCode);
            }
            String normalized = normalizeHeader(row.headerName(), row.headerUse(), routeCode);
            if (!identities.add(row.headerUse() + '\0' + normalized)) {
                throw invalid("Header 规范化后重复: route=" + routeCode + ", header=" + normalized);
            }
            if ("CORS_REQUEST_ALLOWED".equals(row.headerUse())) {
                cors.add(normalized);
                requireMaximum("Route CORS Header", cors.size(), MAX_CORS_HEADERS);
            } else {
                forwarded.add(normalized);
                requireMaximum("Route Forwarded Header", forwarded.size(), MAX_FORWARDED_HEADERS);
            }
        }
        cors.sort(BINARY_STRING);
        forwarded.sort(BINARY_STRING);
        return new HeaderSets(List.copyOf(cors), List.copyOf(forwarded));
    }

    private String normalizeHeader(String headerName, String headerUse, String routeCode) {
        if (headerName == null || headerName.isBlank() || headerName.length() > 255 || !isAscii(headerName)
            || !HEADER_NAME.matcher(headerName).matches() || headerName.indexOf('*') >= 0) {
            throw invalid("Header Name 不合法: route=" + routeCode);
        }
        String normalized = headerName.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_HEADERS.contains(normalized) || normalized.startsWith("x-forwarded-")
            || ("FORWARD_REQUEST".equals(headerUse) && "authorization".equals(normalized))) {
            throw invalid("Header Name 属于 Gateway 保留或 Hop-by-hop Header: " + normalized);
        }
        return normalized;
    }

    private List<String> compileQueries(List<QueryParameterRow> rows, String routeCode) {
        requireMaximum("Route Query Parameter", rows.size(), MAX_QUERY_PARAMETERS);
        Set<String> names = new HashSet<>();
        for (QueryParameterRow row : rows) {
            requirePattern("Query Parameter Name", row.parameterName(), 255, QUERY_NAME);
            if (!names.add(row.parameterName())) {
                throw invalid("Query Parameter 重复: route=" + routeCode + ", name=" + row.parameterName());
            }
        }
        return names.stream().sorted(BINARY_STRING).toList();
    }

    private List<GuardRule> compileGuards(List<GuardRuleRow> rows, GatewaySecurityProfileCode profile,
        String routeCode) {
        requireMaximum("Route Guard Rule", rows.size(), MAX_GUARD_RULES);
        List<GuardRule> result = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (GuardRuleRow row : rows) {
            GatewayGuardRuleType type = parseCode(GatewayGuardRuleType.class, row.ruleType(), "Guard Rule Type");
            GatewayGuardDimension dimension =
                parseCode(GatewayGuardDimension.class, row.dimension(), "Guard Dimension");
            if (!identities.add(guardIdentity(type, dimension))) {
                throw invalid("Guard Type/Dimension 重复: route=" + routeCode);
            }
            validateGuardShape(row, type, routeCode);
            validateGuardDimension(profile, dimension, routeCode);
            result.add(new GuardRule(type, dimension, row.capacity(), row.refillTokens(), row.refillPeriodMillis(),
                row.concurrencyLimit()));
        }
        requiredGuardDimensions(profile).forEach(dimension -> requireGuardPair(identities, dimension, true, routeCode));
        requireGuardPair(identities, GatewayGuardDimension.TENANT, false, routeCode);
        result.sort(GUARD_ORDER);
        return List.copyOf(result);
    }

    private void validateGuardShape(GuardRuleRow row, GatewayGuardRuleType type, String routeCode) {
        if (type == GatewayGuardRuleType.RATE_LIMIT) {
            if (!positive(row.capacity()) || !positive(row.refillTokens()) || !positive(row.refillPeriodMillis())
                || row.capacity() > MAX_RATE_CAPACITY || row.refillTokens() > MAX_RATE_REFILL_TOKENS
                || row.refillPeriodMillis() > MAX_RATE_REFILL_PERIOD_MILLIS || row.concurrencyLimit() != null) {
                throw invalid("RATE_LIMIT Guard 数值形状不合法: route=" + routeCode);
            }
            return;
        }
        if (row.capacity() != null || row.refillTokens() != null || row.refillPeriodMillis() != null
            || !positive(row.concurrencyLimit()) || row.concurrencyLimit() > MAX_CONCURRENCY_LIMIT) {
            throw invalid("CONCURRENCY Guard 数值形状不合法: route=" + routeCode);
        }
    }

    private void validateGuardDimension(GatewaySecurityProfileCode profile, GatewayGuardDimension dimension,
        String routeCode) {
        if (!guardDimensionAllowed(profile, dimension)) {
            throw invalid("当前 Security Profile 不产生可信 Principal/Tenant Guard 维度: route=" + routeCode);
        }
    }

    private void requireGuardPair(Set<String> identities, GatewayGuardDimension dimension, boolean required,
        String routeCode) {
        String rate = guardIdentity(GatewayGuardRuleType.RATE_LIMIT, dimension);
        String concurrency = guardIdentity(GatewayGuardRuleType.CONCURRENCY, dimension);
        boolean hasRate = identities.contains(rate);
        boolean hasConcurrency = identities.contains(concurrency);
        if (hasRate != hasConcurrency || required && !hasRate) {
            throw invalid("Route 缺少完整 " + dimension + " Rate/Concurrency Guard 对: " + routeCode);
        }
    }

    private void rejectOverlappingRoutes(List<Route> routes) {
        Map<RouteShapeKey, RouteOverlapIndex> indexes = new HashMap<>();
        for (Route route : routes.stream().sorted(ROUTE_ORDER).toList()) {
            PathTemplate path = parsePath(route.externalPathTemplate(), "External Path", route.routeCode());
            // 固定路径保留优先权；所有含变量模板进入同一组，不能用变量数量隐式消歧。
            RouteShapeKey key =
                new RouteShapeKey(route.httpMethod(), path.segments().size(), !path.variableNames().isEmpty());
            RouteOverlapIndex index =
                indexes.computeIfAbsent(key, ignored -> new RouteOverlapIndex(path.segments().size()));
            String conflict = index.findOverlap(path.segments());
            if (conflict != null) {
                throw invalid("公共 Route Method/Path 存在可匹配冲突: " + conflict + " / " + route.routeCode());
            }
            index.insert(path.segments(), route.routeCode());
        }
    }

    private PathTemplate parsePath(String value, String fieldName, String routeCode) {
        if (value == null || value.isBlank() || value.length() > 1000 || !isAscii(value) || !value.startsWith("/")
            || value.contains("//") || value.indexOf('\\') >= 0 || value.indexOf('?') >= 0 || value.indexOf('#') >= 0) {
            throw invalid(fieldName + " 不合法: route=" + routeCode);
        }
        validatePercentEncoding(value, fieldName, routeCode);
        String[] rawSegments = value.substring(1).split("/", -1);
        List<Segment> segments = new ArrayList<>();
        Set<String> variables = new HashSet<>();
        if (rawSegments.length == 1 && rawSegments[0].isEmpty()) {
            return new PathTemplate(List.of(), Set.of());
        }
        for (String rawSegment : rawSegments) {
            if (rawSegment.isEmpty() || ".".equals(rawSegment) || "..".equals(rawSegment)) {
                throw invalid(fieldName + " 包含空或 Dot Segment: route=" + routeCode);
            }
            if (rawSegment.startsWith("{") && rawSegment.endsWith("}")) {
                String variable = rawSegment.substring(1, rawSegment.length() - 1);
                if (!VARIABLE_NAME.matcher(variable).matches() || !variables.add(variable)) {
                    throw invalid(fieldName + " 包含非法或重复 Variable: route=" + routeCode);
                }
                segments.add(new Segment(true, variable));
                continue;
            }
            if (rawSegment.indexOf('{') >= 0 || rawSegment.indexOf('}') >= 0 || containsPatternSyntax(rawSegment)
                || !FIXED_PATH_SEGMENT.matcher(rawSegment).matches()) {
                throw invalid(fieldName + " 包含通配、正则或非法 Segment: route=" + routeCode);
            }
            segments.add(new Segment(false, rawSegment));
        }
        return new PathTemplate(List.copyOf(segments), Set.copyOf(variables));
    }

    private void validatePercentEncoding(String path, String fieldName, String routeCode) {
        for (int index = 0; index < path.length(); index++) {
            if (path.charAt(index) != '%') {
                continue;
            }
            if (index + 2 >= path.length() || !isHex(path.charAt(index + 1)) || !isHex(path.charAt(index + 2))) {
                throw invalid(fieldName + " 包含非法 Percent Encoding: route=" + routeCode);
            }
            char high = path.charAt(index + 1);
            char low = path.charAt(index + 2);
            if (Character.isLowerCase(high) || Character.isLowerCase(low)) {
                throw invalid(fieldName + " Percent Encoding 必须使用大写 Hex: route=" + routeCode);
            }
            int decoded = hexValue(high) * 16 + hexValue(low);
            if (decoded == '/' || decoded == '\\' || isUnreserved(decoded)) {
                throw invalid(fieldName + " 包含非规范或危险 Percent Encoding: route=" + routeCode);
            }
            index += 2;
        }
    }

    private boolean containsPatternSyntax(String segment) {
        return segment.indexOf('*') >= 0 || segment.indexOf('[') >= 0 || segment.indexOf(']') >= 0
            || segment.indexOf('(') >= 0 || segment.indexOf(')') >= 0 || segment.indexOf('|') >= 0
            || segment.indexOf('^') >= 0 || segment.indexOf('$') >= 0;
    }

    private boolean isHex(char value) {
        return value >= '0' && value <= '9' || value >= 'a' && value <= 'f' || value >= 'A' && value <= 'F';
    }

    private int hexValue(char value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        return value - 'a' + 10;
    }

    private boolean isUnreserved(int value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z' || value >= '0' && value <= '9'
            || value == '-' || value == '.' || value == '_' || value == '~';
    }

    private boolean positive(Long value) {
        return value != null && value > 0L;
    }

    private boolean parseStatus(String status, String entity, String code) {
        if ("ACTIVE".equals(status)) {
            return true;
        }
        if ("DISABLED".equals(status)) {
            return false;
        }
        throw invalid(entity + " Status 不受支持: code=" + code);
    }

    private <E extends Enum<E> & CodeEnum> E parseCode(Class<E> type, String value, String fieldName) {
        if (value == null) {
            throw invalid(fieldName + " 不得为空");
        }
        for (E candidate : type.getEnumConstants()) {
            if (candidate.getCode().equals(value)) {
                return candidate;
            }
        }
        throw invalid(fieldName + " 不受支持: " + value);
    }

    private String guardIdentity(GatewayGuardRuleType type, GatewayGuardDimension dimension) {
        return type.getCode() + '\0' + dimension.getCode();
    }

    private void requireStableCode(String fieldName, String value, int maxLength) {
        requirePattern(fieldName, value, maxLength, STABLE_CODE);
    }

    private void requireDiscoveryService(String value) {
        requirePattern("Discovery Service Name", value, 255, DISCOVERY_SERVICE);
    }

    private void requirePattern(String fieldName, String value, int maxLength, Pattern pattern) {
        if (value == null || value.isBlank() || value.length() > maxLength || !isAscii(value)
            || !pattern.matcher(value).matches()) {
            throw invalid(fieldName + " 不合法");
        }
    }

    private void requirePositiveId(String fieldName, Long value) {
        if (value == null || value <= 0L) {
            throw invalid(fieldName + " 必须为正数");
        }
    }

    private void requireMaximum(String fieldName, int size, int maximum) {
        if (size > maximum) {
            throw invalid(fieldName + " 数量超过上限: " + maximum);
        }
    }

    private boolean isAscii(String value) {
        return StandardCharsets.US_ASCII.newEncoder().canEncode(value);
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private record TargetState(long id, Target target, boolean active) {
    }

    private record TargetIndex(Map<Long, TargetState> byId, List<TargetState> states) {
    }

    private record RouteState(RouteRow row, TargetState target, GatewayRouteChannel channel, boolean effective) {
    }

    private record RouteIndex(Map<Long, RouteState> byId, List<RouteState> states) {
    }

    private record ChildIndex(Map<Long, List<HeaderRow>> headers, Map<Long, List<QueryParameterRow>> queries,
        Map<Long, List<GuardRuleRow>> guards) {
    }

    private record PermissionBinding(String permissionCode, String scopeCode) {
    }

    private record HeaderSets(List<String> cors, List<String> forwarded) {
    }

    private record PathTemplate(List<Segment> segments, Set<String> variableNames) {
    }

    private record Segment(boolean variable, String value) {
    }

    private record RouteShapeKey(String method, int segmentCount, boolean variable) {
    }

    private static final class RouteOverlapIndex {

        private final int segmentCount;
        private final List<IndexedRoute> linearRoutes = new ArrayList<>();
        private BitSet routes;
        private List<String> routeCodes;
        private List<BitSet> variableRoutesByPosition;
        private int[] variableRouteCounts;
        private List<Map<String, FixedValueRoutes>> fixedValuesByPosition;

        private RouteOverlapIndex(int segmentCount) {
            this.segmentCount = segmentCount;
        }

        private String findOverlap(List<Segment> segments) {
            if (routes == null) {
                return findLinearOverlap(segments);
            }
            int mostSelectivePosition = findMostSelectivePosition(segments);
            if (mostSelectivePosition == NO_COMPATIBLE_ROUTE) {
                return null;
            }
            BitSet candidates = (BitSet)routes.clone();
            if (mostSelectivePosition >= 0) {
                retainCompatible(candidates, segments, mostSelectivePosition);
            }
            for (int position = 0; position < segments.size() && !candidates.isEmpty(); position++) {
                if (position == mostSelectivePosition) {
                    continue;
                }
                Segment segment = segments.get(position);
                if (segment.variable()) {
                    continue;
                }
                retainCompatible(candidates, segments, position);
            }
            int conflictIndex = candidates.nextSetBit(0);
            return conflictIndex < 0 ? null : routeCodes.get(conflictIndex);
        }

        private int findMostSelectivePosition(List<Segment> segments) {
            int selectedPosition = NO_FIXED_POSITION;
            int selectedSize = Integer.MAX_VALUE;
            for (int position = 0; position < segments.size(); position++) {
                Segment segment = segments.get(position);
                if (segment.variable()) {
                    continue;
                }
                FixedValueRoutes sameValue = fixedValuesByPosition.get(position).get(segment.value());
                int compatibleSize = variableRouteCounts[position] + (sameValue == null ? 0 : sameValue.size());
                if (compatibleSize == 0) {
                    return NO_COMPATIBLE_ROUTE;
                }
                if (compatibleSize < selectedSize) {
                    selectedPosition = position;
                    selectedSize = compatibleSize;
                }
            }
            return selectedPosition;
        }

        private void retainCompatible(BitSet candidates, List<Segment> segments, int position) {
            BitSet compatible = (BitSet)variableRoutesByPosition.get(position).clone();
            FixedValueRoutes sameValue = fixedValuesByPosition.get(position).get(segments.get(position).value());
            if (sameValue != null) {
                sameValue.addTo(compatible);
            }
            candidates.and(compatible);
        }

        private void insert(List<Segment> segments, String routeCode) {
            if (routes == null) {
                linearRoutes.add(new IndexedRoute(segments, routeCode));
                if (linearRoutes.size() == BITSET_INDEX_THRESHOLD) {
                    promoteToBitSetIndex();
                }
                return;
            }
            insertIndexed(segments, routeCode);
        }

        private String findLinearOverlap(List<Segment> segments) {
            for (IndexedRoute route : linearRoutes) {
                if (overlaps(route.segments(), segments)) {
                    return route.routeCode();
                }
            }
            return null;
        }

        private boolean overlaps(List<Segment> first, List<Segment> second) {
            for (int position = 0; position < first.size(); position++) {
                Segment left = first.get(position);
                Segment right = second.get(position);
                if (!left.variable() && !right.variable() && !left.value().equals(right.value())) {
                    return false;
                }
            }
            return true;
        }

        private void promoteToBitSetIndex() {
            routes = new BitSet();
            routeCodes = new ArrayList<>(linearRoutes.size());
            variableRoutesByPosition = new ArrayList<>(segmentCount);
            variableRouteCounts = new int[segmentCount];
            fixedValuesByPosition = new ArrayList<>(segmentCount);
            for (int position = 0; position < segmentCount; position++) {
                variableRoutesByPosition.add(new BitSet());
                fixedValuesByPosition.add(new HashMap<>());
            }
            for (IndexedRoute route : linearRoutes) {
                insertIndexed(route.segments(), route.routeCode());
            }
            linearRoutes.clear();
        }

        private void insertIndexed(List<Segment> segments, String routeCode) {
            int routeIndex = routeCodes.size();
            routeCodes.add(routeCode);
            routes.set(routeIndex);
            for (int position = 0; position < segments.size(); position++) {
                Segment segment = segments.get(position);
                if (segment.variable()) {
                    variableRoutesByPosition.get(position).set(routeIndex);
                    variableRouteCounts[position]++;
                    continue;
                }
                fixedValuesByPosition.get(position).computeIfAbsent(segment.value(), ignored -> new FixedValueRoutes())
                    .add(routeIndex);
            }
        }
    }

    private record IndexedRoute(List<Segment> segments, String routeCode) {
    }

    private static final class FixedValueRoutes {

        private int firstIndex = -1;
        private int size;
        private int[] additionalIndexes;
        private BitSet denseIndexes;

        private void add(int routeIndex) {
            if (denseIndexes != null) {
                denseIndexes.set(routeIndex);
                size++;
                return;
            }
            if (size == 0) {
                firstIndex = routeIndex;
                size = 1;
                return;
            }
            ensureCapacity(size);
            additionalIndexes[size - 1] = routeIndex;
            size++;
            if (size == DENSE_VALUE_INDEX_THRESHOLD) {
                promoteToDenseIndex();
            }
        }

        private int size() {
            return size;
        }

        private void addTo(BitSet candidates) {
            if (denseIndexes != null) {
                candidates.or(denseIndexes);
                return;
            }
            candidates.set(firstIndex);
            for (int index = 1; index < size; index++) {
                candidates.set(additionalIndexes[index - 1]);
            }
        }

        private void promoteToDenseIndex() {
            denseIndexes = new BitSet();
            denseIndexes.set(firstIndex);
            for (int index = 1; index < size; index++) {
                denseIndexes.set(additionalIndexes[index - 1]);
            }
            additionalIndexes = null;
        }

        private void ensureCapacity(int requiredSize) {
            if (additionalIndexes == null) {
                additionalIndexes = new int[Math.max(4, requiredSize)];
                return;
            }
            if (requiredSize <= additionalIndexes.length) {
                return;
            }
            int[] expanded = new int[Math.max(additionalIndexes.length * 2, requiredSize)];
            System.arraycopy(additionalIndexes, 0, expanded, 0, additionalIndexes.length);
            additionalIndexes = expanded;
        }
    }
}

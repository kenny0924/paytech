package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.STABLE_CODE_PATTERN;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformGatewayConfigurationClient;
import com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.*;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationManagementApi;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels;

import lombok.AllArgsConstructor;
import com.paytech.operations.console.bff.integration.ConfigurationContractConverter;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;

/** Platform Gateway 管理 DTO 的隔离适配与完整响应校验。 @author Codex */
@Component
@AllArgsConstructor
public final class PlatformGatewayConfigurationClientImpl implements PlatformGatewayConfigurationClient {
    private static final int MAX_ROUTE_PATH_LENGTH = 1_000;
    private static final Pattern STABLE_CODE = Pattern.compile(STABLE_CODE_PATTERN);
    private static final Pattern DISCOVERY_SERVICE =
        Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*");
    private static final Pattern PERMISSION_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");
    private static final Pattern SCOPE_CODE = Pattern.compile("[a-z][a-z0-9]*(?:[.:-][a-z0-9]+)*");
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'+.^_`|~0-9A-Za-z-]+");
    private static final Pattern QUERY_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,254}");
    private static final Set<String> CHANNELS = Set.of("OPERATIONS", "CLIENT", "PARTNER", "CHECKOUT",
        "CLIENT_API", "CHANNEL_CALLBACK");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");
    private static final Set<String> PROFILES = Set.of("OPERATIONS_JWT", "CLIENT_JWT", "PARTNER_JWT",
        "CLIENT_API_CREDENTIAL", "CHECKOUT_SESSION", "CHANNEL_CALLBACK", "PUBLIC");
    private static final Set<String> AUTHORIZATION_MODES = Set.of("PUBLIC", "HUMAN_AUTHENTICATED",
        "HUMAN_PERMISSION", "API_SCOPE", "CHECKOUT_RESOURCE", "CHANNEL_CALLBACK");
    private static final Set<String> REQUEST_COOKIE_MODES = Set.of("DROP", "REQUIRE_PROFILE_COOKIE");
    private static final Set<String> RESPONSE_COOKIE_MODES = Set.of("NONE", "ISSUE_ONLY", "ISSUE_OR_CLEAR", "CLEAR_ONLY");
    private static final Set<String> IDEMPOTENCY_MODES = Set.of("NONE", "REQUIRED");
    private static final Set<String> HEADER_USES = Set.of("CORS_REQUEST_ALLOWED", "FORWARD_REQUEST");
    private static final Set<String> GUARD_TYPES = Set.of("RATE_LIMIT", "CONCURRENCY");
    private static final Set<String> GUARD_DIMENSIONS = Set.of("SHARED_ROUTE", "CLIENT_IP",
        "AUTHENTICATED_PRINCIPAL", "TENANT");

    private final GatewayConfigurationManagementApi management;

    @Override
    public PagingResponseBody<GatewayTargetSummaryResponseBody> targetServices(OperatorContext context, int page, int size,
        GatewayTargetQueryRequestBody query) {
        var result = management.targetServices(context.accountId(), context.sessionId(), page, size,
            query.targetServiceCode(), query.channel(), query.status());
        return mapPage(result, page, size, this::mapTargetSummary, GatewayTargetSummaryResponseBody::targetServiceCode);
    }

    @Override
    public GatewayTargetResponseBody targetService(OperatorContext context, String targetServiceCode) {
        var result = management.targetService(context.accountId(), context.sessionId(), targetServiceCode);
        requireValidResponse(result != null && targetServiceCode.equals(result.targetServiceCode()));
        validateTarget(result.targetServiceCode(), result.discoveryServiceName(), result.channel(), result.status(),
            result.version());
        validateAuditMetadata(result.createdBy(), result.updatedBy(), result.createTime(), result.updateTime());
        return new GatewayTargetResponseBody(result.targetServiceCode(), result.discoveryServiceName(), result.channel(),
            result.status(), result.version(), result.createdBy(), result.updatedBy(), result.createTime(), result.updateTime(), ConfigurationContractConverter.toBff(result.managementProtection()));
    }

    @Override
    public GatewayTargetStatusResponseBody changeTargetStatus(OperatorContext context,
        String targetServiceCode, ChangeGatewayTargetStatusRequestBody request) {
        var result = management.changeTargetStatus(context.accountId(), context.sessionId(),
            targetServiceCode, new GatewayConfigurationModels.ChangeGatewayTargetStatusRequestBody(
                request.status(), request.expectedVersion(), request.reason()));
        requireValidResponse(result != null && targetServiceCode.equals(result.targetServiceCode())
            && request.status().equals(result.status()) && isNextVersion(request.expectedVersion(), result.version()));
        validateStatus(result.status());
        return new GatewayTargetStatusResponseBody(result.targetServiceCode(), result.status(), result.version());
    }

    @Override
    public PagingResponseBody<GatewayRouteSummaryResponseBody> routes(OperatorContext context, int page, int size,
        GatewayRouteQueryRequestBody query) {
        var result = management.routes(context.accountId(), context.sessionId(), page, size, query.routeCode(),
            query.channel(), query.status(), query.targetServiceCode(), query.requiredScopeCode());
        return mapPage(result, page, size, this::mapRouteSummary, GatewayRouteSummaryResponseBody::routeCode);
    }

    @Override
    public GatewayRouteResponseBody route(OperatorContext context, String routeCode) {
        var result = management.route(context.accountId(), context.sessionId(), routeCode);
        requireValidResponse(result != null && routeCode.equals(result.routeCode()));
        validateRouteSummary(result.routeCode(), result.channel(), result.httpMethod(), result.externalPathTemplate(),
            result.targetServiceCode(), result.targetStatus(), result.securityProfileCode(), result.authorizationMode(),
            result.status(), result.version());
        requireValidResponse(validPath(result.downstreamPathTemplate())
            && REQUEST_COOKIE_MODES.contains(result.requestCookieMode())
            && RESPONSE_COOKIE_MODES.contains(result.responseCookieMode())
            && result.idempotencyMode() != null && IDEMPOTENCY_MODES.contains(result.idempotencyMode())
            && result.bodyLimitBytes() >= 0 && result.bodyLimitBytes() <= 65_536
            && result.bodyReceiveTimeoutMillis() > 0 && result.bodyReceiveTimeoutMillis() <= 5_000
            && result.responseTimeoutMillis() > 0 && result.responseTimeoutMillis() <= 10_000);
        validateAuthorization(result.authorizationMode(), result.permissionId(), result.permissionCode(),
            result.requiredScopeCode());
        validateAuditMetadata(result.createdBy(), result.updatedBy(), result.createTime(), result.updateTime());
        List<GatewayRouteHeaderResponseBody> headers = mapHeaders(result.headers());
        List<String> queryParameters = mapQueryParameters(result.queryParameterNames());
        List<GatewayGuardRuleResponseBody> guards = mapGuards(result.guardRules());
        return new GatewayRouteResponseBody(result.routeCode(), result.channel(), result.httpMethod(),
            result.externalPathTemplate(), result.downstreamPathTemplate(), result.targetServiceCode(),
            result.targetStatus(), result.securityProfileCode(), result.authorizationMode(), result.permissionId(),
            result.permissionCode(), result.requiredScopeCode(), result.requestCookieMode(), result.responseCookieMode(),
            result.idempotencyMode(), result.bodyLimitBytes(), result.bodyReceiveTimeoutMillis(), result.responseTimeoutMillis(), result.status(),
            result.version(), headers, queryParameters, guards, result.createdBy(), result.updatedBy(), result.createTime(),
            result.updateTime(), ConfigurationContractConverter.toBff(result.managementProtection()));
    }

    @Override
    public GatewayRouteStatusResponseBody changeRouteStatus(OperatorContext context,
        String routeCode, ChangeGatewayRouteStatusRequestBody request) {
        var result = management.changeRouteStatus(context.accountId(), context.sessionId(), routeCode,
            new GatewayConfigurationModels.ChangeGatewayRouteStatusRequestBody(
                request.status(), request.expectedVersion(), request.reason()));
        requireValidResponse(result != null && routeCode.equals(result.routeCode())
            && request.status().equals(result.status()) && isNextVersion(request.expectedVersion(), result.version()));
        validateStatus(result.status());
        return new GatewayRouteStatusResponseBody(result.routeCode(), result.status(), result.version());
    }

    private GatewayTargetSummaryResponseBody mapTargetSummary(GatewayConfigurationModels.GatewayTargetSummaryResponseBody result) {
        requireValidResponse(result != null);
        validateTarget(result.targetServiceCode(), result.discoveryServiceName(), result.channel(), result.status(),
            result.version());
        return new GatewayTargetSummaryResponseBody(result.targetServiceCode(), result.discoveryServiceName(), result.channel(),
            result.status(), result.version(), ConfigurationContractConverter.toBff(result.managementProtection()));
    }

    private GatewayRouteSummaryResponseBody mapRouteSummary(GatewayConfigurationModels.GatewayRouteSummaryResponseBody result) {
        requireValidResponse(result != null);
        validateRouteSummary(result.routeCode(), result.channel(), result.httpMethod(), result.externalPathTemplate(),
            result.targetServiceCode(), result.targetStatus(), result.securityProfileCode(), result.authorizationMode(),
            result.status(), result.version());
        return new GatewayRouteSummaryResponseBody(result.routeCode(), result.channel(), result.httpMethod(),
            result.externalPathTemplate(), result.targetServiceCode(), result.targetStatus(), result.securityProfileCode(),
            result.authorizationMode(), result.status(), result.version(), ConfigurationContractConverter.toBff(result.managementProtection()));
    }

    private void validateTarget(String code, String discoveryService, String channel, String status, int version) {
        requireValidResponse(valid(STABLE_CODE, code, 128) && valid(DISCOVERY_SERVICE, discoveryService, 255)
            && CHANNELS.contains(channel) && STATUSES.contains(status) && version > 0);
    }

    private void validateRouteSummary(String routeCode, String channel, String method, String externalPath,
        String targetCode, String targetStatus, String profile, String authorizationMode, String status, int version) {
        requireValidResponse(valid(STABLE_CODE, routeCode, 128) && CHANNELS.contains(channel) && METHODS.contains(method)
            && validPath(externalPath) && valid(STABLE_CODE, targetCode, 128) && STATUSES.contains(targetStatus)
            && PROFILES.contains(profile) && AUTHORIZATION_MODES.contains(authorizationMode)
            && STATUSES.contains(status) && version > 0);
    }

    private void validateAuthorization(String mode, String permissionId, String permissionCode, String scopeCode) {
        if ("HUMAN_PERMISSION".equals(mode)) {
            requireValidResponse(nonBlank(permissionId) && valid(PERMISSION_CODE, permissionCode, 128)
                && scopeCode == null);
            return;
        }
        if ("API_SCOPE".equals(mode)) {
            requireValidResponse(permissionId == null && permissionCode == null && valid(SCOPE_CODE, scopeCode, 128));
            return;
        }
        requireValidResponse(permissionId == null && permissionCode == null && scopeCode == null);
    }

    private List<GatewayRouteHeaderResponseBody> mapHeaders(List<GatewayConfigurationModels.GatewayRouteHeaderResponseBody> values) {
        requireValidResponse(values != null && values.size() <= 128);
        Set<String> identities = new HashSet<>();
        return values.stream().map(header -> {
            requireValidResponse(header != null && HEADER_USES.contains(header.headerUse())
                && valid(HEADER_NAME, header.headerName(), 255));
            String identity = header.headerUse() + '\0' + header.headerName().toLowerCase(Locale.ROOT);
            requireValidResponse(identities.add(identity));
            return new GatewayRouteHeaderResponseBody(header.headerName(), header.headerUse());
        }).toList();
    }

    private List<String> mapQueryParameters(List<String> values) {
        requireValidResponse(values != null && values.size() <= 32);
        Set<String> unique = new HashSet<>();
        return values.stream().map(name -> {
            requireValidResponse(valid(QUERY_NAME, name, 255) && unique.add(name));
            return name;
        }).toList();
    }

    private List<GatewayGuardRuleResponseBody> mapGuards(List<GatewayConfigurationModels.GatewayGuardRuleResponseBody> values) {
        requireValidResponse(values != null && values.size() <= 8);
        Set<String> identities = new HashSet<>();
        List<GatewayGuardRuleResponseBody> result = values.stream().map(guard -> {
            requireValidResponse(guard != null && GUARD_TYPES.contains(guard.ruleType())
                && GUARD_DIMENSIONS.contains(guard.dimension())
                && identities.add(guard.ruleType() + '\0' + guard.dimension()));
            validateGuardShape(guard);
            return new GatewayGuardRuleResponseBody(guard.ruleType(), guard.dimension(), guard.capacity(), guard.refillTokens(),
                guard.refillPeriodMillis(), guard.concurrencyLimit());
        }).toList();
        for (String dimension : GUARD_DIMENSIONS) {
            requireValidResponse(identities.contains("RATE_LIMIT\0" + dimension)
                == identities.contains("CONCURRENCY\0" + dimension));
        }
        requireValidResponse(identities.contains("RATE_LIMIT\0SHARED_ROUTE"));
        return result;
    }

    private void validateGuardShape(GatewayConfigurationModels.GatewayGuardRuleResponseBody guard) {
        if ("RATE_LIMIT".equals(guard.ruleType())) {
            requireValidResponse(positiveAtMost(guard.capacity(), 600) && positiveAtMost(guard.refillTokens(), 600)
                && positiveAtMost(guard.refillPeriodMillis(), 60_000) && guard.concurrencyLimit() == null);
            return;
        }
        requireValidResponse(guard.capacity() == null && guard.refillTokens() == null
            && guard.refillPeriodMillis() == null && positiveAtMost(guard.concurrencyLimit(), 50));
    }

    private <S, T> PagingResponseBody<T> mapPage(PagingResponseBody<S> response, int page, int size,
        Function<S, T> mapper, Function<T, String> identity) {
        requireValidResponse(response != null && response.items() != null && response.page() == page
            && response.size() == size && validPageWindow(response.items(), page, size, response.total())
            && response.items().size() <= size);
        List<T> items = response.items().stream().map(mapper).toList();
        Set<String> identities = new HashSet<>();
        requireValidResponse(items.stream().allMatch(item -> identities.add(identity.apply(item))));
        return new PagingResponseBody<>(items, response.page(), response.size(), response.total());
    }

    private boolean validPageWindow(List<?> items, int page, int size, long total) {
        if (total < items.size()) {
            return false;
        }
        long offset = Math.multiplyExact((long) page - 1L, (long) size);
        if (items.isEmpty()) {
            return offset >= total;
        }
        return offset <= total - items.size();
    }

    private void validateStatus(String status) {
        requireValidResponse(STATUSES.contains(status));
    }

    private void validateAuditMetadata(String createdBy, String updatedBy, Instant createTime, Instant updateTime) {
        requireValidResponse(nonBlank(createdBy) && nonBlank(updatedBy) && createTime != null && updateTime != null
            && !updateTime.isBefore(createTime));
    }

    private static boolean valid(Pattern pattern, String value, int maximumLength) {
        return value != null && value.length() <= maximumLength && pattern.matcher(value).matches();
    }

    private static boolean validPath(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_ROUTE_PATH_LENGTH || !value.startsWith("/")
            || value.indexOf('?') >= 0 || value.indexOf('#') >= 0) {
            return false;
        }
        return value.codePoints().allMatch(character -> character >= 0x21 && character <= 0x7e);
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isNextVersion(int expectedVersion, int actualVersion) {
        return (long) actualVersion == (long) expectedVersion + 1L;
    }

    private static boolean positiveAtMost(Long value, long maximum) {
        return value != null && value > 0 && value <= maximum;
    }

    private static void requireValidResponse(boolean condition) {
        if (!condition) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
    }

    /** 保留参数和当前身份，不转发 Idempotency-Key，不增加业务事务。 */
    @Override
    public ConfigurationMutationResponseBody createTarget(OperatorContext context, CreateGatewayTargetRequestBody request) {
        try {
            var result = management.createTarget(context.accountId(), context.sessionId(), ConfigurationContractConverter.toPlatform(request));
            return ConfigurationContractConverter.toBff(result);
        } catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 保留参数和当前身份，不转发 Idempotency-Key，不增加业务事务。 */
    @Override
    public ConfigurationMutationResponseBody updateTarget(OperatorContext context, String targetServiceCode, UpdateGatewayTargetRequestBody request) {
        try {
            var result = management.updateTarget(context.accountId(), context.sessionId(), targetServiceCode, ConfigurationContractConverter.toPlatform(request));
            return ConfigurationContractConverter.toBff(result);
        } catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 保留参数和当前身份，不转发 Idempotency-Key，不增加业务事务。 */
    @Override
    public ConfigurationMutationResponseBody createRoute(OperatorContext context, CreateGatewayRouteRequestBody request) {
        try {
            var result = management.createRoute(context.accountId(), context.sessionId(), ConfigurationContractConverter.toPlatform(request));
            return ConfigurationContractConverter.toBff(result);
        } catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 保留参数和当前身份，不转发 Idempotency-Key，不增加业务事务。 */
    @Override
    public ConfigurationMutationResponseBody updateRoute(OperatorContext context, String routeCode, UpdateGatewayRouteRequestBody request) {
        try {
            var result = management.updateRoute(context.accountId(), context.sessionId(), routeCode, ConfigurationContractConverter.toPlatform(request));
            return ConfigurationContractConverter.toBff(result);
        } catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 保留参数和当前身份，不转发 Idempotency-Key，不增加业务事务。 */
    @Override
    public GatewayConfigurationCapabilitiesResponseBody capabilities(OperatorContext context) {
        var result = management.capabilities(context.accountId(), context.sessionId());
        return ConfigurationContractConverter.toBff(result);
    }
}

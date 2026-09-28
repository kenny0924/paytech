package com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration;

import java.util.List;

/**
 * 脱离持久化实现的 Gateway 当前配置领域投影。
 */
public final class GatewayConfigurationProjection {

    private GatewayConfigurationProjection() {}

    public record LoadedConfiguration(List<TargetRow> targets, List<RouteRow> routes, List<PermissionRow> permissions,
        List<HeaderRow> headers, List<QueryParameterRow> queryParameters, List<GuardRuleRow> guardRules) {

        public LoadedConfiguration {
            targets = immutable("targets", targets);
            routes = immutable("routes", routes);
            permissions = immutable("permissions", permissions);
            headers = immutable("headers", headers);
            queryParameters = immutable("queryParameters", queryParameters);
            guardRules = immutable("guardRules", guardRules);
        }

        private static <T> List<T> immutable(String name, List<T> values) {
            if (values == null || values.stream().anyMatch(value -> value == null)) {
                throw new IllegalArgumentException(name + " 不得为空或包含空元素");
            }
            return List.copyOf(values);
        }
    }

    public record TargetRow(Long targetId, String targetServiceCode, String discoveryServiceName, String channel,
        String status) {
    }

    public record RouteRow(Long routeId, String routeCode, String channel, String httpMethod,
        String externalPathTemplate, String downstreamPathTemplate, Long targetId, String securityProfileCode,
        String authorizationMode, Long permissionId, String requiredScopeCode, String requestCookieMode,
        String responseCookieMode, long bodyLimitBytes, int bodyReceiveTimeoutMillis, int responseTimeoutMillis,
        String status) {
    }

    public record PermissionRow(Long permissionId, String permissionCode) {
    }

    public record HeaderRow(Long routeId, String headerName, String headerUse) {
    }

    public record QueryParameterRow(Long routeId, String parameterName) {
    }

    public record GuardRuleRow(Long routeId, String ruleType, String dimension, Long capacity, Long refillTokens,
        Long refillPeriodMillis, Long concurrencyLimit) {
    }
}

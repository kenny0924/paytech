package com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ManagementProtection;
import java.util.List;

import com.paytech.platform.support.api.enums.GatewayAuthorizationMode;
import com.paytech.platform.support.api.enums.GatewayGuardDimension;
import com.paytech.platform.support.api.enums.GatewayGuardRuleType;
import com.paytech.platform.support.api.enums.GatewayHttpMethod;
import com.paytech.platform.support.api.enums.GatewayRequestCookieMode;
import com.paytech.platform.support.api.enums.GatewayResponseCookieMode;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.api.enums.GatewaySecurityProfileCode;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayRouteHeaderUse;

/** Gateway 配置管理用例在 Processor 与 Domain Service 之间使用的内部模型。 */
public final class GatewayConfigurationManagementModels {

    private GatewayConfigurationManagementModels() {
    }

    public record TargetPageQuery(String actorId, String sessionId, int page, int size, String targetServiceCode,
        String channel, String status) {
    }

    public record TargetDetailQuery(String actorId, String sessionId, String targetServiceCode) {
    }

    public record TargetStatusCommand(String actorId, String sessionId,
        String targetServiceCode, String status, int expectedVersion, String reason) {
    }

    public record RoutePageQuery(String actorId, String sessionId, int page, int size, String routeCode,
        String channel, String status, String targetServiceCode, String requiredScopeCode) {
    }

    public record RouteDetailQuery(String actorId, String sessionId, String routeCode) {
    }

    public record RouteStatusCommand(String actorId, String sessionId, String routeCode,
        String status, int expectedVersion, String reason) {
    }

    public record Page<T>(List<T> items, int page, int size, long total) {
        public Page {
            items = List.copyOf(items);
        }
    }

    public record TargetSummaryResult(String targetServiceCode, String discoveryServiceName,
        GatewayRouteChannel channel, GatewayConfigurationStatus status, int version, ManagementProtection managementProtection) {
    }

    public record TargetDetailResult(String targetServiceCode, String discoveryServiceName, GatewayRouteChannel channel,
        GatewayConfigurationStatus status, int version, long createdBy, long updatedBy, Instant createTime,
        Instant updateTime, ManagementProtection managementProtection) {
    }

    public record TargetStatusResult(String targetServiceCode, GatewayConfigurationStatus status, int version) {
    }

    public record RouteSummaryResult(String routeCode, GatewayRouteChannel channel, GatewayHttpMethod httpMethod,
        String externalPathTemplate, String targetServiceCode, GatewayConfigurationStatus targetStatus,
        GatewaySecurityProfileCode securityProfileCode, GatewayAuthorizationMode authorizationMode,
        GatewayConfigurationStatus status, int version, ManagementProtection managementProtection) {
    }

    public record RouteDetailResult(String routeCode, GatewayRouteChannel channel, GatewayHttpMethod httpMethod,
        String externalPathTemplate, String downstreamPathTemplate, String targetServiceCode,
        GatewayConfigurationStatus targetStatus, GatewaySecurityProfileCode securityProfileCode,
        GatewayAuthorizationMode authorizationMode, Long permissionId, String permissionCode, String requiredScopeCode,
        GatewayRequestCookieMode requestCookieMode, GatewayResponseCookieMode responseCookieMode, long bodyLimitBytes,
        int bodyReceiveTimeoutMillis, int responseTimeoutMillis, GatewayConfigurationStatus status, int version,
        List<RouteHeaderResult> headers, List<String> queryParameterNames, List<GuardRuleResult> guardRules,
        long createdBy, long updatedBy, Instant createTime, Instant updateTime, ManagementProtection managementProtection) {
        public RouteDetailResult {
            headers = List.copyOf(headers);
            queryParameterNames = List.copyOf(queryParameterNames);
            guardRules = List.copyOf(guardRules);
        }
    }

    public record RouteHeaderResult(String headerName, GatewayRouteHeaderUse headerUse) {
    }

    public record GuardRuleResult(GatewayGuardRuleType ruleType, GatewayGuardDimension dimension, Long capacity,
        Long refillTokens, Long refillPeriodMillis, Long concurrencyLimit) {
    }

    public record RouteStatusResult(String routeCode, GatewayConfigurationStatus status, int version) {
    }
}

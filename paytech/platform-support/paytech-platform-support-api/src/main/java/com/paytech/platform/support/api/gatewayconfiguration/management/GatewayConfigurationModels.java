package com.paytech.platform.support.api.gatewayconfiguration.management;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import com.paytech.platform.support.api.configurationmanagement.response.ManagementProtection;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Gateway Target 和 Route 当前配置的管理接口模型。 */
public final class GatewayConfigurationModels {

    private GatewayConfigurationModels() {
    }

    public record GatewayTargetSummaryResponseBody(String targetServiceCode, String discoveryServiceName,
        String channel,
        String status, int version, ManagementProtection managementProtection) {
    }

    public record GatewayTargetResponseBody(String targetServiceCode, String discoveryServiceName, String channel,
        String status, int version, String createdBy, String updatedBy, Instant createTime, Instant updateTime, ManagementProtection managementProtection) {
    }

    public record ChangeGatewayTargetStatusRequestBody(
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status, @Positive int expectedVersion,
        @NotBlank @Size(max = 500) String reason) {
        public ChangeGatewayTargetStatusRequestBody {
            status = status == null ? null : status.strip();
            reason = reason == null ? null : reason.strip();
        }
    }

    public record GatewayTargetStatusResponseBody(String targetServiceCode, String status, int version) {
    }

    public record GatewayRouteSummaryResponseBody(String routeCode, String channel, String httpMethod,
        String externalPathTemplate, String targetServiceCode, String targetStatus, String securityProfileCode,
        String authorizationMode, String status, int version, ManagementProtection managementProtection) {
    }

    public record GatewayRouteResponseBody(String routeCode, String channel, String httpMethod,
        String externalPathTemplate, String downstreamPathTemplate, String targetServiceCode, String targetStatus,
        String securityProfileCode, String authorizationMode, String permissionId, String permissionCode,
        String requiredScopeCode, String requestCookieMode, String responseCookieMode, String idempotencyMode,
        long bodyLimitBytes, int bodyReceiveTimeoutMillis, int responseTimeoutMillis, String status, int version,
        List<GatewayRouteHeaderResponseBody> headers, List<String> queryParameterNames,
        List<GatewayGuardRuleResponseBody> guardRules,
        String createdBy, String updatedBy, Instant createTime, Instant updateTime, ManagementProtection managementProtection) {
        public GatewayRouteResponseBody {
            headers = List.copyOf(headers);
            queryParameterNames = List.copyOf(queryParameterNames);
            guardRules = List.copyOf(guardRules);
        }
    }

    public record GatewayRouteHeaderResponseBody(String headerName, String headerUse) {
    }

    public record GatewayGuardRuleResponseBody(String ruleType, String dimension, Long capacity, Long refillTokens,
        Long refillPeriodMillis, Long concurrencyLimit) {
    }

    public record ChangeGatewayRouteStatusRequestBody(
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status, @Positive int expectedVersion,
        @NotBlank @Size(max = 500) String reason) {
        public ChangeGatewayRouteStatusRequestBody {
            status = status == null ? null : status.strip();
            reason = reason == null ? null : reason.strip();
        }
    }

    public record GatewayRouteStatusResponseBody(String routeCode, String status, int version) {
    }

    /** 新建 Target 固定保存为 DISABLED，业务身份创建后不可修改。 */
    public record CreateGatewayTargetRequestBody(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{2,127}") String targetServiceCode,
        @NotBlank String channel, @NotBlank @Size(max = 255) String discoveryServiceName,
        @NotBlank @Size(max = 500) String reason) {}

    /** Target 只允许修改服务发现名，不改变身份或启停状态。 */
    public record UpdateGatewayTargetRequestBody(@NotBlank @Size(max = 255) String discoveryServiceName,
        @Positive int expectedVersion, @NotBlank @Size(max = 500) String reason) {}

    /** Route 全量创建，状态固定为 DISABLED；idempotencyMode 缺省为 NONE。 */
    public record CreateGatewayRouteRequestBody(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{2,127}") String routeCode, @NotBlank String channel,
        @NotBlank String httpMethod,
        @NotBlank @Size(max = 1000) String externalPathTemplate,
        @NotBlank @Size(max = 1000) String downstreamPathTemplate,
        @NotBlank @Size(max = 128) String targetServiceCode,
        @NotBlank String securityProfileCode, @NotBlank String authorizationMode,
        @Pattern(regexp = "[1-9][0-9]{0,18}") String permissionId,
        @Size(max = 128) String requiredScopeCode,
        @NotBlank String requestCookieMode, @NotBlank String responseCookieMode,
        @Size(max = 64) String idempotencyMode,
        @Min(0) long bodyLimitBytes, @Positive int bodyReceiveTimeoutMillis,
        @Positive int responseTimeoutMillis,
        @NotNull @Size(max = 128) List<@NotNull @Valid GatewayRouteHeaderRequestBody> headers,
        @NotNull @Size(max = 32) List<@NotBlank String> queryParameterNames,
        @NotNull @Size(max = 8) List<@NotNull @Valid GatewayGuardRuleRequestBody> guardRules,
        @NotBlank @Size(max = 500) String reason) {}

    /** 全量替换 Route 配置及子集合，身份和状态沿用当前记录；idempotencyMode 缺省时保持当前值。 */
    public record UpdateGatewayRouteRequestBody(
        @NotBlank String httpMethod,
        @NotBlank @Size(max = 1000) String externalPathTemplate,
        @NotBlank @Size(max = 1000) String downstreamPathTemplate,
        @NotBlank @Size(max = 128) String targetServiceCode,
        @NotBlank String securityProfileCode, @NotBlank String authorizationMode,
        @Pattern(regexp = "[1-9][0-9]{0,18}") String permissionId,
        @Size(max = 128) String requiredScopeCode,
        @NotBlank String requestCookieMode, @NotBlank String responseCookieMode,
        @Size(max = 64) String idempotencyMode,
        @Min(0) long bodyLimitBytes, @Positive int bodyReceiveTimeoutMillis,
        @Positive int responseTimeoutMillis,
        @NotNull @Size(max = 128) List<@NotNull @Valid GatewayRouteHeaderRequestBody> headers,
        @NotNull @Size(max = 32) List<@NotBlank String> queryParameterNames,
        @NotNull @Size(max = 8) List<@NotNull @Valid GatewayGuardRuleRequestBody> guardRules,
        @Positive int expectedVersion, @NotBlank @Size(max = 500) String reason) {}

    /** 只配置 Header 名称和用途，不包含 Header 值。 */
    public record GatewayRouteHeaderRequestBody(@NotBlank @Size(max = 255) String headerName, @NotBlank String headerUse) {}

    /** Rate 与并发的互斥字段由同一 Gateway 编译器校验。 */
    public record GatewayGuardRuleRequestBody(@NotBlank String ruleType, @NotBlank String dimension,
        @Positive Long capacity, @Positive Long refillTokens, @Positive Long refillPeriodMillis, @Positive Long concurrencyLimit) {}

    /** 编译器当前支持的组合和上限，与保存校验共用来源。 */
    public record GatewayConfigurationCapabilitiesResponseBody(List<ChannelCapability> channels, List<String> httpMethods, Limits limits,
        List<String> forbiddenHeaderNames, List<String> headerUses) {}

    /** 单一 Channel 的合法安全配置。 */
    public record ChannelCapability(String channel, List<ProfileCapability> profiles) {}

    /** Guard 必需维度均需 Rate/Concurrency 成对，TENANT 可选时也必须成对。 */
    public record ProfileCapability(String securityProfileCode, List<String> authorizationModes,
        List<String> requestCookieModes, List<String> responseCookieModes, List<String> idempotencyModes,
        List<String> allowedGuardDimensions, List<String> requiredGuardDimensions) {}

    /** 所有时间为毫秒，Body 为字节，集合数按完整聚合计算。 */
    public record Limits(long maxBodyLimitBytes, int maxBodyReceiveTimeoutMillis, int maxResponseTimeoutMillis,
        long maxRateCapacity, long maxRateRefillTokens, long maxRateRefillPeriodMillis, long maxConcurrencyLimit,
        int maxTargets, int maxRoutes, int maxCorsHeaders, int maxForwardedHeaders, int maxQueryParameters,
        int maxGuardRules) {}
}

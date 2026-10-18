package com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration;
import java.util.List;
/** Gateway 编译器向管理消费者提供的部署能力。 */
public final class GatewayConfigurationCapabilities {
    private GatewayConfigurationCapabilities() {}

    /** 编译器当前支持的组合和上限，与保存校验共用来源。 */
    public record Capabilities(List<ChannelCapability> channels, List<String> httpMethods, Limits limits,
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

package com.paytech.platform.support.ext.rest.gatewayconfiguration;

import java.util.List;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationMutation;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCapabilities.Capabilities;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.Page;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteDetailResult;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteStatusResult;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.RouteSummaryResult;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetDetailResult;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetStatusResult;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.TargetSummaryResult;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.configurationmanagement.response.ManagementProtection;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChannelCapability;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayConfigurationCapabilitiesResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayGuardRuleResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteHeaderResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteStatusResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteSummaryResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayTargetResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayTargetStatusResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayTargetSummaryResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.Limits;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ProfileCapability;

/**
 * Gateway 当前配置的内部结果与 HTTP 响应体之间的转换
 * <li>只投影 Code、十进制字符串主键与维护边界，不查询数据或判断业务资格。</li>
 */
final class GatewayConfigurationManagementConverter {

    /** 仅提供静态转换。 */
    private GatewayConfigurationManagementConverter() { }

    /** Target 分页。 */
    static PagingResponseBody<GatewayTargetSummaryResponseBody> targetPage(Page<TargetSummaryResult> page) {
        List<GatewayTargetSummaryResponseBody> items = page.items().stream()
            .map(item -> new GatewayTargetSummaryResponseBody(item.targetServiceCode(), item.discoveryServiceName(),
                item.channel().getCode(), item.status().getCode(), item.version(), protection(item.managementProtection())))
            .toList();
        return new PagingResponseBody<>(items, page.page(), page.size(), page.total());
    }

    /** Target 详情；操作者 ID 保持十进制字符串。 */
    static GatewayTargetResponseBody target(TargetDetailResult result) {
        return new GatewayTargetResponseBody(result.targetServiceCode(), result.discoveryServiceName(),
            result.channel().getCode(), result.status().getCode(), result.version(), Long.toString(result.createdBy()),
            Long.toString(result.updatedBy()), result.createTime(), result.updateTime(), protection(result.managementProtection()));
    }

    /** Target 启停结果。 */
    static GatewayTargetStatusResponseBody targetStatus(TargetStatusResult result) {
        return new GatewayTargetStatusResponseBody(result.targetServiceCode(), result.status().getCode(), result.version());
    }

    /** Route 分页。 */
    static PagingResponseBody<GatewayRouteSummaryResponseBody> routePage(Page<RouteSummaryResult> page) {
        List<GatewayRouteSummaryResponseBody> items = page.items().stream()
            .map(item -> new GatewayRouteSummaryResponseBody(item.routeCode(), item.channel().getCode(),
                item.httpMethod().getCode(), item.externalPathTemplate(), item.targetServiceCode(),
                item.targetStatus().getCode(), item.securityProfileCode().getCode(),
                item.authorizationMode().getCode(), item.status().getCode(), item.version(), protection(item.managementProtection())))
            .toList();
        return new PagingResponseBody<>(items, page.page(), page.size(), page.total());
    }

    /** Route 详情，含 Header、Query 与 Guard。 */
    static GatewayRouteResponseBody route(RouteDetailResult result) {
        List<GatewayRouteHeaderResponseBody> headers = result.headers().stream()
            .map(header -> new GatewayRouteHeaderResponseBody(header.headerName(), header.headerUse().getCode()))
            .toList();
        List<GatewayGuardRuleResponseBody> guards = result.guardRules().stream()
            .map(rule -> new GatewayGuardRuleResponseBody(rule.ruleType().getCode(), rule.dimension().getCode(),
                rule.capacity(), rule.refillTokens(), rule.refillPeriodMillis(), rule.concurrencyLimit()))
            .toList();
        return new GatewayRouteResponseBody(result.routeCode(), result.channel().getCode(),
            result.httpMethod().getCode(), result.externalPathTemplate(), result.downstreamPathTemplate(),
            result.targetServiceCode(), result.targetStatus().getCode(), result.securityProfileCode().getCode(),
            result.authorizationMode().getCode(), result.permissionId() == null ? null : result.permissionId().toString(),
            result.permissionCode(), result.requiredScopeCode(), result.requestCookieMode().getCode(),
            result.responseCookieMode().getCode(), result.idempotencyMode().getCode(), result.bodyLimitBytes(),
            result.bodyReceiveTimeoutMillis(), result.responseTimeoutMillis(), result.status().getCode(), result.version(),
            headers, result.queryParameterNames(), guards, Long.toString(result.createdBy()), Long.toString(result.updatedBy()),
            result.createTime(), result.updateTime(), protection(result.managementProtection()));
    }

    /** Route 启停结果。 */
    static GatewayRouteStatusResponseBody routeStatus(RouteStatusResult result) {
        return new GatewayRouteStatusResponseBody(result.routeCode(), result.status().getCode(), result.version());
    }

    /** 配置保存回执；数据库主键保持十进制字符串 Wire，不经过浮点数。 */
    static ConfigurationMutationResponseBody mutation(ConfigurationMutation result) {
        return new ConfigurationMutationResponseBody(result.objectType(), Long.toString(result.objectId()),
            result.objectCode(), result.status(), result.version());
    }

    /** 已授权的编译器能力，不复制配置规则。 */
    static GatewayConfigurationCapabilitiesResponseBody capabilities(Capabilities result) {
        var limit = result.limits();
        return new GatewayConfigurationCapabilitiesResponseBody(result.channels().stream().map(c ->
            new ChannelCapability(c.channel(), c.profiles().stream().map(p -> new ProfileCapability(
                p.securityProfileCode(), p.authorizationModes(), p.requestCookieModes(), p.responseCookieModes(),
                p.idempotencyModes(), p.allowedGuardDimensions(), p.requiredGuardDimensions())).toList())).toList(),
            result.httpMethods(),
            new Limits(limit.maxBodyLimitBytes(), limit.maxBodyReceiveTimeoutMillis(), limit.maxResponseTimeoutMillis(),
                limit.maxRateCapacity(), limit.maxRateRefillTokens(), limit.maxRateRefillPeriodMillis(),
                limit.maxConcurrencyLimit(), limit.maxTargets(), limit.maxRoutes(), limit.maxCorsHeaders(),
                limit.maxForwardedHeaders(), limit.maxQueryParameters(), limit.maxGuardRules()),
            result.forbiddenHeaderNames(), result.headerUses());
    }

    /** UI 与实际服务端操作使用同一维护边界。 */
    static ManagementProtection protection(
        com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ManagementProtection value) {
        return new ManagementProtection(value.editableFields(), value.statusChangeAllowed(), value.reason());
    }
}

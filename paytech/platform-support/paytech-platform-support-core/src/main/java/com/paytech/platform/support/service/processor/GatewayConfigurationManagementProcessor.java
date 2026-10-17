package com.paytech.platform.support.service.processor;

import java.util.List;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.*;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCommands.*;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationMutation;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChangeGatewayRouteStatusRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChangeGatewayTargetStatusRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayGuardRuleResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteHeaderResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteStatusResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteSummaryResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayTargetResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayTargetStatusResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayTargetSummaryResponseBody;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.*;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.accesscontrol.domainservice.service.GatewayConfigurationManagementService;

import lombok.AllArgsConstructor;

/** Gateway 当前配置用例的事务和资源锁编排。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GatewayConfigurationManagementProcessor {
    private static final Pattern STABLE_CODE = Pattern.compile("[a-z][a-z0-9-]{2,127}");
    private final OperationsTransactionExecutor transactions;
    private final GatewayConfigurationManagementService management;

    public PagingResponseBody<GatewayTargetSummaryResponseBody> targets(String actor, String session, int page,
        int size, String code, String channel, String status) {
        return execute(() -> targetPage(management.targets(
                new TargetPageQuery(actor, session, page, size, code, channel, status))));
    }

    public GatewayTargetResponseBody target(String actor, String session, String code) {
        return execute(() -> targetBody(management.target(new TargetDetailQuery(actor, session, code))));
    }

    @ResourceLock(prefix = "gateway-configuration:catalog")
    public GatewayTargetStatusResponseBody changeTargetStatus(String actor, String session, String code,
        ChangeGatewayTargetStatusRequestBody request) {
        TargetStatusCommand command = normalize(targetCommand(actor, session, code, request));
        return transactionalCommand(() -> targetStatusBody(management.changeTargetStatus(command)));
    }

    public PagingResponseBody<GatewayRouteSummaryResponseBody> routes(String actor, String session, int page, int size,
        String code, String channel, String status, String targetCode, String requiredScopeCode) {
        return execute(() -> routePage(management.routes(
                new RoutePageQuery(actor, session, page, size, code, channel, status, targetCode, requiredScopeCode))));
    }

    public GatewayRouteResponseBody route(String actor, String session, String code) {
        return execute(() -> routeBody(management.route(new RouteDetailQuery(actor, session, code))));
    }

    @ResourceLock(prefix = "gateway-configuration:catalog")
    public GatewayRouteStatusResponseBody changeRouteStatus(String actor, String session, String code,
        ChangeGatewayRouteStatusRequestBody request) {
        RouteStatusCommand command = normalize(routeCommand(actor, session, code, request));
        return transactionalCommand(() -> routeStatusBody(management.changeRouteStatus(command)));
    }

    private PagingResponseBody<GatewayTargetSummaryResponseBody> targetPage(Page<TargetSummaryResult> page) {
        List<GatewayTargetSummaryResponseBody> items = page.items().stream()
            .map(item -> new GatewayTargetSummaryResponseBody(item.targetServiceCode(), item.discoveryServiceName(),
                item.channel().getCode(), item.status().getCode(), item.version(), protection(item.managementProtection())))
            .toList();
        return new PagingResponseBody<>(items, page.page(), page.size(), page.total());
    }

    private GatewayTargetResponseBody targetBody(TargetDetailResult result) {
        return new GatewayTargetResponseBody(result.targetServiceCode(), result.discoveryServiceName(),
            result.channel().getCode(), result.status().getCode(), result.version(), Long.toString(result.createdBy()),
            Long.toString(result.updatedBy()), result.createTime(), result.updateTime(), protection(result.managementProtection()));
    }

    private GatewayTargetStatusResponseBody targetStatusBody(TargetStatusResult result) {
        return new GatewayTargetStatusResponseBody(result.targetServiceCode(), result.status().getCode(),
            result.version());
    }

    private PagingResponseBody<GatewayRouteSummaryResponseBody> routePage(Page<RouteSummaryResult> page) {
        List<GatewayRouteSummaryResponseBody> items = page.items().stream()
            .map(item -> new GatewayRouteSummaryResponseBody(item.routeCode(), item.channel().getCode(),
                item.httpMethod().getCode(), item.externalPathTemplate(), item.targetServiceCode(),
                item.targetStatus().getCode(), item.securityProfileCode().getCode(),
                item.authorizationMode().getCode(), item.status().getCode(), item.version(), protection(item.managementProtection())))
            .toList();
        return new PagingResponseBody<>(items, page.page(), page.size(), page.total());
    }

    private GatewayRouteResponseBody routeBody(RouteDetailResult result) {
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
            result.responseCookieMode().getCode(), result.idempotencyMode().getCode(), result.bodyLimitBytes(), result.bodyReceiveTimeoutMillis(),
            result.responseTimeoutMillis(), result.status().getCode(), result.version(), headers,
            result.queryParameterNames(), guards, Long.toString(result.createdBy()), Long.toString(result.updatedBy()),
            result.createTime(), result.updateTime(), protection(result.managementProtection()));
    }

    /** UI 与实际服务端操作使用同一维护边界。 */
    private com.paytech.platform.support.api.configurationmanagement.response.ManagementProtection protection(
        com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ManagementProtection value) {
        return new com.paytech.platform.support.api.configurationmanagement.response.ManagementProtection(
            value.editableFields(), value.statusChangeAllowed(), value.reason());
    }

    private GatewayRouteStatusResponseBody routeStatusBody(RouteStatusResult result) {
        return new GatewayRouteStatusResponseBody(result.routeCode(), result.status().getCode(), result.version());
    }

    private TargetStatusCommand targetCommand(String actor, String session, String code,
        ChangeGatewayTargetStatusRequestBody request) {
        return new TargetStatusCommand(actor, session, code, request == null ? null : request.status(),
            request == null ? 0 : request.expectedVersion(), request == null ? null : request.reason());
    }

    private RouteStatusCommand routeCommand(String actor, String session, String code,
        ChangeGatewayRouteStatusRequestBody request) {
        return new RouteStatusCommand(actor, session, code, request == null ? null : request.status(),
            request == null ? 0 : request.expectedVersion(), request == null ? null : request.reason());
    }

    private TargetStatusCommand normalize(TargetStatusCommand command) {
        if (command == null || command.expectedVersion() <= 0) {
            throw new InvalidParameterException("Target 状态命令及正版本不能为空");
        }
        return new TargetStatusCommand(command.actorId(), command.sessionId(),
            requiredCode(command.targetServiceCode(), "Target Service Code"), requiredStatus(command.status()),
            command.expectedVersion(), reason(command.reason()));
    }

    private RouteStatusCommand normalize(RouteStatusCommand command) {
        if (command == null || command.expectedVersion() <= 0) {
            throw new InvalidParameterException("Route 状态命令及正版本不能为空");
        }
        return new RouteStatusCommand(command.actorId(), command.sessionId(),
            requiredCode(command.routeCode(), "Route Code"), requiredStatus(command.status()),
            command.expectedVersion(), reason(command.reason()));
    }

    private String requiredCode(String value, String field) {
        if (value == null || !STABLE_CODE.matcher(value).matches()) {
            throw new InvalidParameterException(field + " 必须为 3 至 128 位小写 kebab-case");
        }
        return value;
    }

    private String requiredStatus(String value) {
        String status = value == null ? null : value.strip();
        if (java.util.Arrays.stream(GatewayConfigurationStatus.values())
            .noneMatch(candidate -> candidate.getCode().equals(status))) {
            throw new InvalidParameterException(status == null ? "Status 不能为空" : "Status 不是支持的稳定代码");
        }
        return status;
    }

    private String reason(String value) {
        String reason = value == null ? "" : value.strip();
        if (reason.isEmpty() || reason.length() > 500) {
            throw new InvalidParameterException("变更原因必须为 1 至 500 个字符");
        }
        return reason;
    }

    private <T> T transactionalCommand(Supplier<T> operation) {
        try {
            return transactions.serializableWrite(operation);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            String cause = String.valueOf(exception.getMostSpecificCause().getMessage());
            if (!(cause.contains("uk_gateway_target_code") || cause.contains("uk_gateway_route_code") || cause.contains("uk_gateway_route_path"))) throw exception;
            throw new com.paytech.foundation.common.error.BizCheckFailException("配置编码或 Method/Path 已存在");
        } catch (ConcurrencyFailureException exception) {
            throw new ConcurrentRequestException("Gateway 配置发生并发冲突，请刷新后重试", exception);
        }
    }

    /** 授权与目录投影继续保留原有一致性读快照。 */
    private <T> T execute(Supplier<T> operation) {
        return transactions.query(operation);
    }

    /** 创建在资源锁内执行；重复创建由唯一编码拒绝。 */
    @ResourceLock(prefix = "gateway-configuration:catalog")
    public ConfigurationMutationResponseBody createTarget(String actor, String session,
        CreateGatewayTargetRequestBody r) {
        return saveTarget(actor, session, new TargetWrite(r.targetServiceCode(), r.channel(),
            r.discoveryServiceName(), 0, r.reason()));
    }

    /** 更新身份来自 Path，版本必须为正数，不能隐式转为创建。 */
    @ResourceLock(prefix = "gateway-configuration:catalog")
    public ConfigurationMutationResponseBody updateTarget(String actor, String session, String code,
        UpdateGatewayTargetRequestBody r) {
        requireVersion(r.expectedVersion());
        return saveTarget(actor, session, new TargetWrite(code, null, r.discoveryServiceName(),
            r.expectedVersion(), r.reason()));
    }

    /** Route 子项按请求顺序规范化后保存。 */
    @ResourceLock(prefix = "gateway-configuration:catalog")
    public ConfigurationMutationResponseBody createRoute(String actor, String session,
        CreateGatewayRouteRequestBody r) {
        return saveRoute(actor, session, new RouteWrite(r.routeCode(), r.channel(),
            new RouteConfiguration(r.httpMethod(), r.externalPathTemplate(), r.downstreamPathTemplate(), r.targetServiceCode(), r.securityProfileCode(), r.authorizationMode(), permissionId(r.permissionId()), r.requiredScopeCode(), r.requestCookieMode(), r.responseCookieMode(), r.idempotencyMode(), r.bodyLimitBytes(), r.bodyReceiveTimeoutMillis(), r.responseTimeoutMillis(),
            r.headers().stream().map(h -> new Header(h.headerName(), h.headerUse())).toList(),
            r.queryParameterNames(), r.guardRules().stream().map(g -> new Guard(g.ruleType(), g.dimension(),
                g.capacity(), g.refillTokens(), g.refillPeriodMillis(), g.concurrencyLimit())).toList()), 0, r.reason()));
    }

    /** 固定 Channel 和状态从事务内当前记录取得。 */
    @ResourceLock(prefix = "gateway-configuration:catalog")
    public ConfigurationMutationResponseBody updateRoute(String actor, String session, String code,
        UpdateGatewayRouteRequestBody r) {
        requireVersion(r.expectedVersion());
        return saveRoute(actor, session, new RouteWrite(code, null,
            new RouteConfiguration(r.httpMethod(), r.externalPathTemplate(), r.downstreamPathTemplate(), r.targetServiceCode(), r.securityProfileCode(), r.authorizationMode(), permissionId(r.permissionId()), r.requiredScopeCode(), r.requestCookieMode(), r.responseCookieMode(), r.idempotencyMode(), r.bodyLimitBytes(), r.bodyReceiveTimeoutMillis(), r.responseTimeoutMillis(),
            r.headers().stream().map(h -> new Header(h.headerName(), h.headerUse())).toList(),
            r.queryParameterNames(), r.guardRules().stream().map(g -> new Guard(g.ruleType(), g.dimension(),
                g.capacity(), g.refillTokens(), g.refillPeriodMillis(), g.concurrencyLimit())).toList()), r.expectedVersion(), r.reason()));
    }

    /** 只在完成无外部调用的业务事务后返回 HTTP 回执。 */
    private ConfigurationMutationResponseBody saveTarget(String actor, String session, TargetWrite command) {
        return transactionalCommand(() -> mutationBody(management.saveTarget(actor, session, command)));
    }

    /** 和 Target 共用既有 Catalog 锁与候选事务边界。 */
    private ConfigurationMutationResponseBody saveRoute(String actor, String session, RouteWrite command) {
        return transactionalCommand(() -> mutationBody(management.saveRoute(actor, session, command)));
    }

    /** 数据库主键保持十进制字符串 Wire，不经过浮点数。 */
    private ConfigurationMutationResponseBody mutationBody(ConfigurationMutation result) {
        return new ConfigurationMutationResponseBody(result.objectType(), Long.toString(result.objectId()),
            result.objectCode(), result.status(), result.version());
    }

    /** 正数版本是更新请求的必要身份。 */
    private void requireVersion(int version) {
        if (version <= 0) throw new InvalidParameterException("expectedVersion 必须为正数");
    }

    /** 权限 ID 不允许溢出或隐式截断。 */
    private Long permissionId(String value) {
        if (value == null) return null;
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new InvalidParameterException("permissionId 必须是有效的十进制主键");
        }
    }
    /** 仅转换已授权的编译器能力，不复制配置规则。 */
    public GatewayConfigurationCapabilitiesResponseBody capabilities(String actor, String session) {
        var result = management.capabilities(actor, session);
        var limit = result.limits();
        return new GatewayConfigurationCapabilitiesResponseBody(result.channels().stream().map(c ->
            new ChannelCapability(c.channel(), c.profiles().stream().map(p -> new ProfileCapability(
                p.securityProfileCode(), p.authorizationModes(), p.requestCookieModes(), p.responseCookieModes(),
                p.idempotencyModes(), p.allowedGuardDimensions(), p.requiredGuardDimensions())).toList())).toList(), result.httpMethods(),
            new Limits(limit.maxBodyLimitBytes(), limit.maxBodyReceiveTimeoutMillis(), limit.maxResponseTimeoutMillis(),
                limit.maxRateCapacity(), limit.maxRateRefillTokens(), limit.maxRateRefillPeriodMillis(),
                limit.maxConcurrencyLimit(), limit.maxTargets(), limit.maxRoutes(), limit.maxCorsHeaders(),
                limit.maxForwardedHeaders(), limit.maxQueryParameters(), limit.maxGuardRules()),
            result.forbiddenHeaderNames(), result.headerUses());
    }
}

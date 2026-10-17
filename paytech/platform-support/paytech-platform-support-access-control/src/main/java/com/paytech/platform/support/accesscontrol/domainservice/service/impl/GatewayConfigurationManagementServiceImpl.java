package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationMutation;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCommands.*;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.*;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayManagementProtection;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.*;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Identity;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.ManagementRouteContracts;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.api.enums.GatewayAuthorizationMode;
import com.paytech.platform.support.api.enums.GatewayConfigurationErrorCode;
import com.paytech.platform.support.api.enums.GatewayIdempotencyMode;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayRouteManagement;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayRouteRoot;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.GatewayTargetManagement;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.GatewayChange;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.StatusVersion;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.GatewayStatusChanged;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCompiler;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementModels.*;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementState.RouteDetails;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementState.RouteQuery;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationManagementState.TargetQuery;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.LoadedConfiguration;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.RouteRow;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.TargetRow;
import com.paytech.platform.support.accesscontrol.domainservice.repository.GatewayConfigurationManagementRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.GatewayConfigurationRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.GatewayConfigurationManagementService;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService.ManagementContext;

import lombok.AllArgsConstructor;

/** Gateway Target/Route 当前配置的权限校验、候选编译和原子状态命令。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GatewayConfigurationManagementServiceImpl implements GatewayConfigurationManagementService {
    private static final String READ_PERMISSION = "GATEWAY_CONFIG_READ";
    private static final String MANAGE_PERMISSION = "GATEWAY_CONFIG_MANAGE";
    private static final Pattern STABLE_CODE = Pattern.compile("[a-z][a-z0-9-]{2,127}");

    private final Clock clock;
    private final IdGenerator ids;
    private final com.paytech.platform.support.accesscontrol.domainservice.repository.ApiScopeRepository apiScopes;
    private final OperationsAuditService audit;
    private final GatewayConfigurationCompiler compiler;
    private final OperationsManagementPolicyService policy;
    private final GatewayConfigurationRepository configurations;
    private final GatewayConfigurationManagementRepository gateway;

    @Override
    public Page<TargetSummaryResult> targets(TargetPageQuery request) {
        requireReadPermission(request.actorId(), request.sessionId());
        TargetQuery query = new TargetQuery(optionalCode(request.targetServiceCode(), "Target Service Code"),
            optionalEnum(GatewayRouteChannel.class, request.channel(), "Channel"),
            optionalEnum(GatewayConfigurationStatus.class, request.status(), "Status"));
        LoadedConfiguration catalog = configurations.loadCurrentConfiguration();
        List<TargetSummaryResult> items = gateway.targets(query, offset(request.page(), request.size()), request.size())
            .stream().map(row -> targetSummary(row, catalog)).toList();
        return new Page<>(items, request.page(), request.size(), gateway.targetCount(query));
    }

    @Override
    public TargetDetailResult target(TargetDetailQuery request) {
        requireReadPermission(request.actorId(), request.sessionId());
        return targetResult(requireTarget(requiredCode(request.targetServiceCode(), "Target Service Code")), configurations.loadCurrentConfiguration());
    }

    @Override
    public TargetStatusResult changeTargetStatus(TargetStatusCommand command) {
        command = normalize(command);
        var actor = requireManagementPermission(command.actorId(), command.sessionId());
        String requiredCode = requiredCode(command.targetServiceCode(), "Target Service Code");
        GatewayConfigurationStatus next = requiredStatus(command.status());
        GatewayTargetManagement current = requireTarget(requiredCode);
        requireChange(current.status(), current.version(), next, command.expectedVersion());
        GatewayManagementProtection.requireStatusMutable(GatewayManagementProtection.protectedTarget(
            configurations.loadCurrentConfiguration(), current.targetId()));
        LoadedConfiguration candidate =
            targetCandidate(configurations.loadCurrentConfiguration(), current.targetId(), next);
        if (next == GatewayConfigurationStatus.ACTIVE) {
            requireActivePermissions(candidate, current.targetId());
        }
        compileCandidate(candidate);
        Instant now = now();
        if (gateway.updateTargetStatus(current.targetId(), next, current.version(), actor.account().id(), now) != 1) {
            conflict();
        }
        var response = new TargetStatusResult(current.targetServiceCode(), next, current.version() + 1);
        var auditCommand = command;
        audit.record(() -> new GatewayStatusChanged(auditCommand.actorId(), actor.member().tenantId(),
            new GatewayChange(new Target("GATEWAY_TARGET", current.targetServiceCode()), auditCommand.sessionId(),
                new StatusVersion(current.status().getCode(), current.version()),
                new StatusVersion(next.getCode(), response.version()), auditCommand.reason()), now));
        return response;
    }

    @Override
    public Page<RouteSummaryResult> routes(RoutePageQuery request) {
        requireReadPermission(request.actorId(), request.sessionId());
        RouteQuery query = new RouteQuery(optionalCode(request.routeCode(), "Route Code"),
            optionalEnum(GatewayRouteChannel.class, request.channel(), "Channel"),
            optionalEnum(GatewayConfigurationStatus.class, request.status(), "Status"),
            optionalCode(request.targetServiceCode(), "Target Service Code"), request.requiredScopeCode() == null ? null
                : com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs.scopeCode(request.requiredScopeCode()));
        LoadedConfiguration catalog = configurations.loadCurrentConfiguration();
        List<RouteSummaryResult> items = gateway.routes(query, offset(request.page(), request.size()), request.size())
            .stream().map(row -> routeSummary(row, catalog)).toList();
        return new Page<>(items, request.page(), request.size(), gateway.routeCount(query));
    }

    @Override
    public RouteDetailResult route(RouteDetailQuery request) {
        requireReadPermission(request.actorId(), request.sessionId());
        RouteDetails details = gateway.route(requiredCode(request.routeCode(), "Route Code"));
        if (details == null) {
            notFound("Route", request.routeCode());
        }
        return routeResult(details, configurations.loadCurrentConfiguration());
    }

    @Override
    public RouteStatusResult changeRouteStatus(RouteStatusCommand command) {
        command = normalize(command);
        var actor = requireManagementPermission(command.actorId(), command.sessionId());
        String requiredCode = requiredCode(command.routeCode(), "Route Code");
        GatewayConfigurationStatus next = requiredStatus(command.status());
        GatewayRouteRoot current = gateway.routeRoot(requiredCode);
        if (current == null) {
            notFound("Route", requiredCode);
        }
        requireChange(current.status(), current.version(), next, command.expectedVersion());
        var protection = routeProtection(configurations.loadCurrentConfiguration(), current.routeId());
        GatewayManagementProtection.requireStatusMutable(!protection.statusChangeAllowed());
        if (next == GatewayConfigurationStatus.ACTIVE) {
            requireActivePermission(current.authorizationMode(), current.permissionId());
        }
        LoadedConfiguration candidate =
            routeCandidate(configurations.loadCurrentConfiguration(), current.routeId(), next);
        compileCandidate(candidate);
        Instant now = now();
        if (gateway.updateRouteStatus(current.routeId(), next, current.version(), actor.account().id(), now) != 1) {
            conflict();
        }
        var response = new RouteStatusResult(current.routeCode(), next, current.version() + 1);
        var auditCommand = command;
        audit.record(() -> new GatewayStatusChanged(auditCommand.actorId(), actor.member().tenantId(),
            new GatewayChange(new Target("GATEWAY_ROUTE", current.routeCode()), auditCommand.sessionId(),
                new StatusVersion(current.status().getCode(), current.version()),
                new StatusVersion(next.getCode(), response.version()), auditCommand.reason()), now));
        return response;
    }

    /** 当前权限检查在业务事务内；重复创建由唯一编码拒绝，更新由版本 CAS 拒绝。 */
    @Override
    public ConfigurationMutation saveTarget(String actorId, String session, TargetWrite command) {
        var actor = requireManagementPermission(actorId, session);
        boolean create = command.expectedVersion() == 0;
        GatewayTargetManagement current = gateway.target(command.code());
        requireWriteVersion(create, current == null ? null : current.version(), command.expectedVersion(), "Target");
        LoadedConfiguration catalog = configurations.loadCurrentConfiguration();
        long id = create ? ids.nextId() : current.targetId();
        String channel = create ? command.channel() : current.channel().getCode();
        String status = create ? "DISABLED" : current.status().getCode();
        if (!create && !java.util.Objects.equals(current.discoveryServiceName(), command.discoveryServiceName())
            && GatewayManagementProtection.protectedTarget(catalog, id)) {
            throw new BizCheckFailException("内置管理 Target 的服务发现身份不可修改");
        }
        TargetRow row = new TargetRow(id, command.code(), command.discoveryServiceName(), channel, status);
        var targets = new java.util.ArrayList<>(catalog.targets().stream().filter(t -> t.targetId() != id).toList());
        targets.add(row);
        compileEditedCandidate(new LoadedConfiguration(targets, catalog.routes(), catalog.permissions(), catalog.headers(),
            catalog.queryParameters(), catalog.guardRules()));
        if (!create && current.discoveryServiceName().equals(command.discoveryServiceName())) {
            return completeUnchanged(new ConfigurationMutation("GATEWAY_TARGET", id, command.code(), status,
                current.version()));
        }
        Instant now = now();
        int changed = create ? gateway.insertTarget(row, actor.account().id(), now)
            : gateway.updateTarget(row, command.expectedVersion(), actor.account().id(), now);
        if (changed != 1) conflict();
        var result = new ConfigurationMutation("GATEWAY_TARGET", id, command.code(), status,
            create ? 1 : current.version() + 1);
        var fields = List.of(new ConfigurationFieldChange(ConfigurationField.DISCOVERY_SERVICE_NAME,
            current == null ? null : current.discoveryServiceName(), command.discoveryServiceName()),
            new ConfigurationFieldChange(ConfigurationField.CHANNEL, current == null ? null : channel, channel),
            new ConfigurationFieldChange(ConfigurationField.STATUS, current == null ? null : status, status));
        recordConfiguration(actor, actorId, result, command.expectedVersion(), fields, command.reason(), now);
        return result;
    }

    /** 全量候选编译与 Root/子项保存共享一个事务，禁用配置也不跳过校验。 */
    @Override
    public ConfigurationMutation saveRoute(String actorId, String session, RouteWrite command) {
        var actor = requireManagementPermission(actorId, session);
        boolean create = command.expectedVersion() == 0;
        var current = gateway.route(command.code());
        requireWriteVersion(create, current == null ? null : current.route().version(), command.expectedVersion(), "Route");
        var config = command.configuration().withDefaultIdempotencyMode(create ? GatewayIdempotencyMode.NONE.getCode()
            : current.route().idempotencyMode().getCode());
        var permission = config.permissionId() == null ? null : gateway.permissionIdentity(config.permissionId());
        LoadedConfiguration catalog = configurations.loadCurrentConfiguration();
        var target = requireTarget(config.targetServiceCode());
        long id = create ? ids.nextId() : current.route().routeId();
        String channel = create ? command.channel() : current.route().channel().getCode();
        String status = create ? "DISABLED" : current.route().status().getCode();
        if (config.permissionId() != null && (permission == null || !permission.active()
            && (create || !java.util.Objects.equals(current.route().permissionId(), config.permissionId())))) {
            throw new BizCheckFailException("新绑定的 Permission 必须存在且启用");
        }
        RouteRow row = new RouteRow(id, command.code(), channel, config.httpMethod(), config.externalPathTemplate(),
            config.downstreamPathTemplate(), target.targetId(), config.securityProfileCode(), config.authorizationMode(),
            config.permissionId(), config.requiredScopeCode(), config.requestCookieMode(), config.responseCookieMode(),
            config.idempotencyMode(), config.bodyLimitBytes(), config.bodyReceiveTimeoutMillis(),
            config.responseTimeoutMillis(), status);
        var before = catalog.routes().stream().filter(r -> r.routeId() == id).findFirst().orElse(null);
        GatewayManagementProtection.requireEditable(before, row);
        ManagementRouteContracts.requireBody(row);
        if (GatewayAuthorizationMode.API_SCOPE.getCode().equals(row.authorizationMode())
            && (before == null || !GatewayAuthorizationMode.API_SCOPE.getCode().equals(before.authorizationMode())
                || !java.util.Objects.equals(before.requiredScopeCode(), row.requiredScopeCode()))) {
            String scopeCode = com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs.scopeCode(row.requiredScopeCode());
            if (apiScopes.scope(scopeCode) == null) { throw new BizCheckFailException("新绑定的 Scope 必须先登记目录"); }
        }
        var headers = config.headers().stream().map(h -> new HeaderRow(id, h.headerName(), h.headerUse())).toList();
        var queries = config.queryParameterNames().stream().map(q -> new QueryParameterRow(id, q)).toList();
        var guards = config.guardRules().stream().map(g -> new GuardRuleRow(id, g.ruleType(), g.dimension(),
            g.capacity(), g.refillTokens(), g.refillPeriodMillis(), g.concurrencyLimit())).toList();
        requireManagementChildren(before, catalog, headers, queries);
        var permissions = new java.util.ArrayList<>(catalog.permissions());
        if (permission != null && permissions.stream().noneMatch(p -> p.permissionId() == permission.permissionId())) {
            permissions.add(new PermissionRow(permission.permissionId(), permission.permissionCode()));
        }
        LoadedConfiguration candidate = new LoadedConfiguration(catalog.targets(),
            replace(catalog.routes(), r -> r.routeId() == id, List.of(row)), permissions,
            replace(catalog.headers(), h -> h.routeId() == id, headers),
            replace(catalog.queryParameters(), q -> q.routeId() == id, queries),
            replace(catalog.guardRules(), g -> g.routeId() == id, guards));
        compileEditedCandidate(candidate);
        if (!create && config.equals(configuration(current))) {
            return completeUnchanged(new ConfigurationMutation("GATEWAY_ROUTE", id, command.code(), status,
                current.route().version()));
        }
        Instant now = now();
        int changed = create ? gateway.insertRoute(row, actor.account().id(), now)
            : gateway.updateRoute(row, command.expectedVersion(), actor.account().id(), now);
        if (changed != 1) conflict();
        gateway.replaceRouteChildren(id, headers, queries, guards);
        var result = new ConfigurationMutation("GATEWAY_ROUTE", id, command.code(), status,
            create ? 1 : current.route().version() + 1);
        recordConfiguration(actor, actorId, result, command.expectedVersion(),
            routeChanges(current == null ? null : configuration(current), config), command.reason(), now);
        return result;
    }

    /** 创建不覆盖已有身份，更新不能退化成 upsert。 */
    private void requireWriteVersion(boolean create, Integer current, int expected, String kind) {
        if (create && current != null) throw new BizCheckFailException(kind + " 编码已经存在");
        if (!create && current == null) notFound(kind, "当前编码");
        if (!create && (expected <= 0 || current != expected)) conflict();
    }

    /** 无变化保存返回当前版本，不制造重复成功审计。 */
    private ConfigurationMutation completeUnchanged(ConfigurationMutation result) {
        audit.suppressReplay();
        return result;
    }

    /** 子项替换只是候选构造，不发起任何外部调用。 */
    private <T> List<T> replace(List<T> rows, java.util.function.Predicate<T> belongs, List<T> replacement) {
        var result = new java.util.ArrayList<>(rows.stream().filter(belongs.negate()).toList());
        result.addAll(replacement);
        return result;
    }

    /** 管理协议必需参数不能在传输编辑时丢失。 */
    private void requireManagementChildren(RouteRow before, LoadedConfiguration catalog,
        List<HeaderRow> headers, List<QueryParameterRow> queries) {
        if (before == null || !GatewayManagementProtection.protectedRoute(before)) return;
        ManagementRouteContracts.requireTransport(before, headers, queries);

    }

    /** 从完整详情构造同一规范化配置，集合输入顺序不会产生虚假版本。 */
    private RouteConfiguration configuration(RouteDetails details) {
        var r = details.route();
        return new RouteConfiguration(r.httpMethod().getCode(), r.externalPathTemplate(), r.downstreamPathTemplate(),
            r.targetServiceCode(), r.securityProfileCode().getCode(), r.authorizationMode().getCode(), r.permissionId(),
            r.requiredScopeCode(), r.requestCookieMode().getCode(), r.responseCookieMode().getCode(),
            r.idempotencyMode().getCode(), r.bodyLimitBytes(),
            r.bodyReceiveTimeoutMillis(), r.responseTimeoutMillis(), details.headers().stream()
                .map(h -> new Header(h.headerName(), h.headerUse().getCode())).toList(), details.queryParameterNames(),
            details.guardRules().stream().map(g -> new Guard(g.ruleType().getCode(), g.dimension().getCode(),
                g.capacity(), g.refillTokens(), g.refillPeriodMillis(), g.concurrencyLimit())).toList());
    }

    /** 当次审计只投影明确配置字段；集合指纹不存放凭据或请求值。 */
    private List<ConfigurationFieldChange> routeChanges(RouteConfiguration before, RouteConfiguration after) {
        var oldFields = routeAuditFields(before);
        var newFields = routeAuditFields(after);
        return newFields.entrySet().stream()
            .filter(e -> !java.util.Objects.equals(oldFields.get(e.getKey()), e.getValue()))
            .map(e -> new ConfigurationFieldChange(e.getKey(), oldFields.get(e.getKey()), e.getValue())).toList();
    }

    /** 受控配置映射仅供本模块审计差异使用，不作为 HTTP 响应。 */
    private java.util.Map<ConfigurationField, String> routeAuditFields(RouteConfiguration value) {
        var fields = new java.util.EnumMap<ConfigurationField, String>(ConfigurationField.class);
        if (value == null) return fields;
        fields.put(ConfigurationField.HTTP_METHOD, value.httpMethod());
        fields.put(ConfigurationField.EXTERNAL_PATH, value.externalPathTemplate());
        fields.put(ConfigurationField.DOWNSTREAM_PATH, value.downstreamPathTemplate());
        fields.put(ConfigurationField.TARGET, value.targetServiceCode());
        fields.put(ConfigurationField.SECURITY_PROFILE, value.securityProfileCode());
        fields.put(ConfigurationField.AUTHORIZATION_MODE, value.authorizationMode());
        fields.put(ConfigurationField.PERMISSION, java.util.Objects.toString(value.permissionId(), null));
        fields.put(ConfigurationField.SCOPE, value.requiredScopeCode());
        fields.put(ConfigurationField.REQUEST_COOKIE_MODE, value.requestCookieMode());
        fields.put(ConfigurationField.RESPONSE_COOKIE_MODE, value.responseCookieMode());
        fields.put(ConfigurationField.IDEMPOTENCY_MODE, value.idempotencyMode());
        fields.put(ConfigurationField.BODY_LIMIT_BYTES, Long.toString(value.bodyLimitBytes()));
        fields.put(ConfigurationField.BODY_RECEIVE_TIMEOUT_MILLIS, Integer.toString(value.bodyReceiveTimeoutMillis()));
        fields.put(ConfigurationField.RESPONSE_TIMEOUT_MILLIS, Integer.toString(value.responseTimeoutMillis()));
        fields.put(ConfigurationField.HEADERS_DIGEST, digest(value.headers()));
        fields.put(ConfigurationField.QUERIES_DIGEST, digest(value.queryParameterNames()));
        fields.put(ConfigurationField.GUARDS_DIGEST, digest(value.guardRules()));
        return fields;
    }

    /** JDK 规定必须提供 SHA-256；仅在内存计算，无远程密码服务调用。 */
    private String digest(Object value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }

    /** 在业务现场冻结安全字段，审计异步持久化沿用现有机制。 */
    private void recordConfiguration(ManagementContext actor, String actorId, ConfigurationMutation result,
        int beforeVersion, List<ConfigurationFieldChange> fields, String reason, Instant now) {
        audit.record(() -> new ConfigurationChanged(new Identity(actorId, actor.member().tenantId()),
            new Target(result.objectType(), result.objectCode()), beforeVersion, result.version(), fields, reason, now));
    }

    private void requireReadPermission(String actorId, String sessionId) {
        policy.requirePermission(actorId, sessionId, READ_PERMISSION, MANAGE_PERMISSION);
    }

    private ManagementContext requireManagementPermission(String actorId, String sessionId) {
        return policy.authorizeManagement(actorId, sessionId, null, List.of(), null, null, false,
            MANAGE_PERMISSION);
    }

    private GatewayTargetManagement requireTarget(String code) {
        GatewayTargetManagement target = gateway.target(code);
        if (target == null) {
            notFound("Target", code);
        }
        return target;
    }

    private void requireChange(GatewayConfigurationStatus current, int version, GatewayConfigurationStatus next,
        int expectedVersion) {
        if (version != expectedVersion) {
            conflict();
        }
        if (current == next) {
            throw new BizCheckFailException("Gateway 配置已经处于请求状态");
        }
    }

    private void requireActivePermissions(LoadedConfiguration candidate, long targetId) {
        candidate.routes().stream().filter(route -> route.targetId() == targetId)
            .filter(route -> GatewayConfigurationStatus.ACTIVE.getCode().equals(route.status()))
            .filter(route -> GatewayAuthorizationMode.HUMAN_PERMISSION.getCode().equals(route.authorizationMode()))
            .forEach(route -> requireActivePermission(GatewayAuthorizationMode.HUMAN_PERMISSION, route.permissionId()));
    }

    private void requireActivePermission(GatewayAuthorizationMode mode, Long permissionId) {
        if (mode == GatewayAuthorizationMode.HUMAN_PERMISSION
            && (permissionId == null || !gateway.permissionActive(permissionId))) {
            throw new ConcurrentRequestException("Route 引用的 Permission 当前不可用");
        }
    }

    private LoadedConfiguration targetCandidate(LoadedConfiguration current, long targetId,
        GatewayConfigurationStatus next) {
        List<TargetRow> targets =
            current.targets().stream()
                .map(target -> target.targetId() != targetId ? target : new TargetRow(target.targetId(),
                    target.targetServiceCode(), target.discoveryServiceName(), target.channel(), next.getCode()))
                .toList();
        return new LoadedConfiguration(targets, current.routes(), current.permissions(), current.headers(),
            current.queryParameters(), current.guardRules());
    }

    private LoadedConfiguration routeCandidate(LoadedConfiguration current, long routeId,
        GatewayConfigurationStatus next) {
        List<RouteRow> routes = current.routes().stream()
            .map(route -> route.routeId() != routeId ? route : routeWithStatus(route, next)).toList();
        return new LoadedConfiguration(current.targets(), routes, current.permissions(), current.headers(),
            current.queryParameters(), current.guardRules());
    }

    private RouteRow routeWithStatus(RouteRow route, GatewayConfigurationStatus status) {
        return new RouteRow(route.routeId(), route.routeCode(), route.channel(), route.httpMethod(),
            route.externalPathTemplate(), route.downstreamPathTemplate(), route.targetId(), route.securityProfileCode(),
            route.authorizationMode(), route.permissionId(), route.requiredScopeCode(), route.requestCookieMode(),
            route.responseCookieMode(), route.idempotencyMode(), route.bodyLimitBytes(),
            route.bodyReceiveTimeoutMillis(), route.responseTimeoutMillis(), status.getCode());
    }

    private void compileCandidate(LoadedConfiguration candidate) {
        try {
            compiler.compile(candidate);
        } catch (IllegalArgumentException exception) {
            throw new ConcurrentRequestException("Gateway 当前配置候选存在全局冲突", exception);
        }
    }

    /** 管理维护信息来自当前完整目录，避免按每行反复访问数据库。 */
    private com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ManagementProtection routeProtection(
        LoadedConfiguration catalog, long id) {
        return GatewayManagementProtection.route(catalog.routes().stream().filter(r -> r.routeId() == id)
            .findFirst().orElseThrow(() -> new IllegalStateException("Route 详情与目录不一致")));
    }

    /** 新配置字段校验失败是请求错误，不能误报为版本竞争。 */
    private void compileEditedCandidate(LoadedConfiguration candidate) {
        try { compiler.compile(candidate); }
        catch (IllegalArgumentException exception) {
            throw new InvalidParameterException(exception.getMessage());
        }
    }

    private TargetSummaryResult targetSummary(GatewayTargetManagement target, LoadedConfiguration catalog) {
        return new TargetSummaryResult(target.targetServiceCode(), target.discoveryServiceName(), target.channel(),
            target.status(), target.version(), GatewayManagementProtection.target(catalog, target.targetId()));
    }

    private TargetDetailResult targetResult(GatewayTargetManagement target, LoadedConfiguration catalog) {
        return new TargetDetailResult(target.targetServiceCode(), target.discoveryServiceName(), target.channel(),
            target.status(), target.version(), target.createdBy(), target.updatedBy(), target.createTime(),
            target.updateTime(), GatewayManagementProtection.target(catalog, target.targetId()));
    }

    private RouteSummaryResult routeSummary(GatewayRouteManagement route, LoadedConfiguration catalog) {
        return new RouteSummaryResult(route.routeCode(), route.channel(), route.httpMethod(),
            route.externalPathTemplate(), route.targetServiceCode(), route.targetStatus(), route.securityProfileCode(),
            route.authorizationMode(), route.status(), route.version(), routeProtection(catalog, route.routeId()));
    }

    private RouteDetailResult routeResult(RouteDetails details, LoadedConfiguration catalog) {
        GatewayRouteManagement route = details.route();
        List<RouteHeaderResult> headers = details.headers().stream()
            .map(header -> new RouteHeaderResult(header.headerName(), header.headerUse())).toList();
        List<
            GuardRuleResult> guards =
                details
                    .guardRules().stream().map(rule -> new GuardRuleResult(rule.ruleType(), rule.dimension(),
                        rule.capacity(), rule.refillTokens(), rule.refillPeriodMillis(), rule.concurrencyLimit()))
                    .toList();
        return new RouteDetailResult(route.routeCode(), route.channel(), route.httpMethod(),
            route.externalPathTemplate(), route.downstreamPathTemplate(), route.targetServiceCode(),
            route.targetStatus(), route.securityProfileCode(), route.authorizationMode(), route.permissionId(),
            route.permissionCode(), route.requiredScopeCode(), route.requestCookieMode(), route.responseCookieMode(),
            route.idempotencyMode(), route.bodyLimitBytes(), route.bodyReceiveTimeoutMillis(), route.responseTimeoutMillis(), route.status(),
            route.version(), headers, details.queryParameterNames(), guards, route.createdBy(), route.updatedBy(),
            route.createTime(), route.updateTime(), routeProtection(catalog, route.routeId()));
    }

    private TargetStatusCommand normalize(TargetStatusCommand command) {
        if (command == null || command.expectedVersion() <= 0) {
            throw new InvalidParameterException("Target 状态命令及正版本不能为空");
        }
        return new TargetStatusCommand(command.actorId(), command.sessionId(), 
            command.targetServiceCode(), normalizeStatus(command.status()), command.expectedVersion(),
            normalizeReason(command.reason()));
    }

    private RouteStatusCommand normalize(RouteStatusCommand command) {
        if (command == null || command.expectedVersion() <= 0) {
            throw new InvalidParameterException("Route 状态命令及正版本不能为空");
        }
        return new RouteStatusCommand(command.actorId(), command.sessionId(), 
            command.routeCode(), normalizeStatus(command.status()), command.expectedVersion(),
            normalizeReason(command.reason()));
    }

    private String normalizeStatus(String status) {
        return status == null ? null : status.strip();
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new InvalidParameterException("变更原因必须为 1 至 500 个字符");
        }
        return normalized;
    }

    private String optionalCode(String value, String field) {
        return value == null ? null : requiredCode(value, field);
    }

    private String requiredCode(String value, String field) {
        if (value == null || !STABLE_CODE.matcher(value).matches()) {
            throw new InvalidParameterException(field + " 必须为 3 至 128 位小写 kebab-case");
        }
        return value;
    }

    private GatewayConfigurationStatus requiredStatus(String value) {
        GatewayConfigurationStatus status = optionalEnum(GatewayConfigurationStatus.class, value, "Status");
        if (status == null) {
            throw new InvalidParameterException("Status 不能为空");
        }
        return status;
    }

    private <T extends Enum<T> & CodeEnum> T optionalEnum(Class<T> type, String value, String field) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(type.getEnumConstants()).filter(candidate -> candidate.getCode().equals(value)).findFirst()
            .orElseThrow(() -> new InvalidParameterException(field + " 不是支持的稳定代码"));
    }

    private int offset(int page, int size) {
        return Math.multiplyExact(page - 1, size);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private void notFound(String resource, String code) {
        throw new FailException(GatewayConfigurationErrorCode.GATEWAY_CONFIGURATION_NOT_FOUND,
            resource + " 不存在: " + code);
    }

    private void conflict() {
        throw new ConcurrentRequestException("Gateway 配置已被更新，请刷新后重试");
    }
    /** 查询能力不会绕过配置查看权限。 */
    @Override
    public com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationCapabilities.Capabilities
        capabilities(String actor, String session) {
        policy.requirePermission(actor, session, READ_PERMISSION);
        return compiler.capabilities();
    }
}

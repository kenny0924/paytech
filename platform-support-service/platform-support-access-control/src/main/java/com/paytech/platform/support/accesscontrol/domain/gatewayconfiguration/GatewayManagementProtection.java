package com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration;

import java.util.List;
import java.util.Objects;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ManagementProtection;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.*;

/** 管理合同的维护边界；不参与 Gateway 转发或业务 Target 注册。 */
public final class GatewayManagementProtection {
    private static final List<String> MANAGEMENT_PREFIXES = List.of("/iam/v1/", "/gateway-admin/v1/",
        "/console-admin/v1/", "/permission-admin/v1/", "/auth/v1/", "/authentication/v1/",
        "/authorization/v1/", "/bootstrap/v1/");

    private GatewayManagementProtection() {}

    /** 同时识别外部和下游管理路径，避免先换路径绕过维护边界。 */
    public static boolean protectedRoute(RouteRow route) {
        return "OPERATIONS".equals(route.channel())
            && (managementPath(route.externalPathTemplate()) || managementPath(route.downstreamPathTemplate()));
    }

    /** 被管理合同引用的服务发现身份不能通过普通管理页面迁移。 */
    public static boolean protectedTarget(LoadedConfiguration catalog, long targetId) {
        return catalog.routes().stream().anyMatch(r -> r.targetId() == targetId && protectedRoute(r));
    }

    /** 页面和直接 API 调用使用同一维护判断。 */
    public static ManagementProtection target(LoadedConfiguration catalog, long targetId) {
        return protectedTarget(catalog, targetId) ? ManagementProtection.protectedFields()
            : ManagementProtection.editable("discoveryServiceName");
    }

    /** Route 身份和安全绑定受保护，容量仍受编译器校验。 */
    public static ManagementProtection route(RouteRow route) {
        String[] transport = {"headers", "queryParameterNames", "guardRules", "bodyLimitBytes",
            "bodyReceiveTimeoutMillis", "responseTimeoutMillis"};
        return protectedRoute(route) ? ManagementProtection.protectedFields(transport)
            : ManagementProtection.editable("httpMethod", "externalPathTemplate", "downstreamPathTemplate",
                "targetServiceCode", "securityProfileCode", "authorizationMode", "permissionId", "requiredScopeCode",
                "requestCookieMode", "responseCookieMode", "headers", "queryParameterNames", "guardRules",
                "bodyLimitBytes", "bodyReceiveTimeoutMillis", "responseTimeoutMillis");
    }

    /** 禁止通过配置管理创建或改写管理入口的身份与鉴权链。 */
    public static void requireEditable(RouteRow before, RouteRow after) {
        if (!protectedRoute(after) && (before == null || !protectedRoute(before))) return;
        if (before == null || !Objects.equals(identity(before), identity(after))) {
            throw new BizCheckFailException("内置管理 Route 的身份和安全绑定不可修改");
        }

    }

    /** 启停保护对初始停用的管理配置同样适用。 */
    public static void requireStatusMutable(boolean protectedObject) {
        if (protectedObject) throw new BizCheckFailException("内置管理入口不允许通过普通管理接口启停");
    }

    /** 身份投影允许空 Permission/Scope，故不使用拒绝 null 的 List.of。 */
    private static List<Object> identity(RouteRow row) {
        return java.util.Arrays.asList(row.channel(), row.httpMethod(), row.externalPathTemplate(),
            row.downstreamPathTemplate(), row.targetId(), row.securityProfileCode(), row.authorizationMode(),
            row.permissionId(), row.requiredScopeCode(), row.requestCookieMode(), row.responseCookieMode());
    }

    /** 公共前缀移除一次后按完整路径段识别。 */
    private static boolean managementPath(String path) {
        if (path == null) return false;
        String relative = path.startsWith("/api/operations/") ? path.substring("/api/operations".length()) : path;
        return MANAGEMENT_PREFIXES.stream().anyMatch(relative::startsWith);
    }
}

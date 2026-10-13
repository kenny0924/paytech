package com.paytech.platform.support.test.builder;

import java.util.List;

/**
 * Operations 全局目录的稳定测试基线，标识与编码沿用正式初始化数据 platform-support-initial-data.sql。
 *
 * <p>只包含 Platform 自有权限；Risk 决策配置权限由 Risk 管理，不进入本服务测试基线。
 */
public final class OperationsCatalog {

    /** 内部运营租户。 */
    public static final long INTERNAL_TENANT_ID = 900000000000000500L;

    /** 内置平台管理员角色。 */
    public static final long ADMIN_ROLE_ID = 900000000000000510L;

    /** 内置运营只读角色。 */
    public static final long VIEWER_ROLE_ID = 900000000000000511L;

    /** Operations Realm 当前签名密钥 kid。 */
    public static final String OPERATIONS_KID = "operations-mock-1";

    /** Customer Realm 当前签名密钥 kid。 */
    public static final String CUSTOMER_KID = "customer-mock-1";

    /** 正式权限目录中由 Platform 消费的条目。 */
    public static final List<Permission> PERMISSIONS = List.of(
        new Permission(900000000000000520L, "OPERATIONS_ACCOUNT_READ", "USER_ACCOUNT", "READ", "LOW", true),
        new Permission(900000000000000522L, "OPERATIONS_ROLE_READ", "ROLE", "READ", "LOW", true),
        new Permission(900000000000000523L, "OPERATIONS_AUDIT_READ", "AUDIT_EVENT", "READ", "LOW", true),
        new Permission(900000000000000524L, "OPERATIONS_SYSTEM_READ", "OPERATIONS_SYSTEM", "READ", "LOW", true),
        new Permission(900000000000000526L, "OPERATIONS_ACCOUNT_CREATE", "USER_ACCOUNT", "CREATE", "HIGH", false),
        new Permission(900000000000000527L, "OPERATIONS_ACCOUNT_DISABLE", "USER_ACCOUNT", "DISABLE", "HIGH", false),
        new Permission(900000000000000528L, "OPERATIONS_ACCOUNT_ASSIGN_ROLES", "USER_ACCOUNT", "ASSIGN_ROLES", "HIGH",
            false),
        new Permission(900000000000000529L, "OPERATIONS_ROLE_CREATE", "ROLE", "CREATE", "HIGH", false),
        new Permission(900000000000000530L, "OPERATIONS_ROLE_UPDATE", "ROLE", "UPDATE", "HIGH", false),
        new Permission(900000000000000531L, "OPERATIONS_ROLE_DELETE", "ROLE", "DELETE", "HIGH", false),
        new Permission(900000000000000532L, "OPERATIONS_ACCOUNT_DELETE", "USER_ACCOUNT", "DELETE", "HIGH", false),
        new Permission(900000000000000533L, "OPERATIONS_ACCOUNT_ENABLE", "USER_ACCOUNT", "ENABLE", "HIGH", false),
        new Permission(900000000000000534L, "GATEWAY_CONFIG_READ", "GATEWAY_CONFIGURATION", "READ", "LOW", true),
        new Permission(900000000000000535L, "GATEWAY_CONFIG_MANAGE", "GATEWAY_CONFIGURATION", "MANAGE", "HIGH", false),
        new Permission(900000000000041101L, "CONSOLE_RESOURCE_READ", "CONSOLE_RESOURCE", "READ", "LOW", false),
        new Permission(900000000000041102L, "CONSOLE_RESOURCE_MANAGE", "CONSOLE_RESOURCE", "MANAGE", "HIGH", false),
        new Permission(900000000000041103L, "PERMISSION_READ", "PERMISSION", "READ", "LOW", false),
        new Permission(900000000000041104L, "PERMISSION_MANAGE", "PERMISSION", "MANAGE", "HIGH", false),
        new Permission(900000000000051101L, "API_CREDENTIAL_READ", "API_CREDENTIAL", "READ", "LOW", false),
        new Permission(900000000000051102L, "API_CREDENTIAL_REVOKE", "API_CREDENTIAL", "REVOKE", "CRITICAL", false),
        new Permission(900000000000051103L, "API_SCOPE_READ", "API_SCOPE", "READ", "LOW", false),
        new Permission(900000000000051104L, "API_SCOPE_MANAGE", "API_SCOPE", "MANAGE", "HIGH", false),
        new Permission(900000000000061101L, "TENANT_ACCESS_READ", "ACCESS_TENANT", "READ", "LOW", false),
        new Permission(900000000000061102L, "TENANT_ACCESS_MANAGE", "ACCESS_TENANT", "MANAGE", "HIGH", false),
        new Permission(900000000000061103L, "TENANT_MEMBER_READ", "TENANT_MEMBER", "READ", "LOW", false),
        new Permission(900000000000071101L, "EXTERNAL_API_READ", "EXTERNAL_API", "READ", "LOW", false),
        new Permission(900000000000071102L, "EXTERNAL_API_MANAGE", "EXTERNAL_API", "MANAGE", "HIGH", false));

    private OperationsCatalog() {
    }

    /** 按编码查找目录条目，不存在时测试失败。 */
    public static Permission permission(String code) {
        return PERMISSIONS.stream().filter(permission -> permission.code().equals(code)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("测试权限目录不包含：" + code));
    }

    /**
     * 目录权限。
     *
     * @param viewer 正式初始化数据是否同时授予只读角色
     */
    public record Permission(long id, String code, String resourceType, String action, String risk, boolean viewer) {
    }
}

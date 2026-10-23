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
        new Permission(900000000000071102L, "EXTERNAL_API_MANAGE", "EXTERNAL_API", "MANAGE", "HIGH", false),
        new Permission(900000000000090101L, "WEBHOOK_READ", "WEBHOOK", "READ", "LOW", false),
        new Permission(900000000000090102L, "WEBHOOK_MANAGE", "WEBHOOK", "MANAGE", "HIGH", false),
        new Permission(900000000000086101L, "NOTIFICATION_READ", "NOTIFICATION", "READ", "LOW", false),
        new Permission(900000000000086102L, "NOTIFICATION_MANAGE", "NOTIFICATION", "MANAGE", "HIGH", false),
        new Permission(900000000000096101L, "NOTIFICATION_CREDENTIAL_MANAGE", "NOTIFICATION", "CREDENTIAL_MANAGE", "HIGH",
            false),
        new Permission(900000000000097101L, "FINANCE_ACCOUNTING_READ", "FINANCE_ACCOUNTING", "READ", "LOW", false),
        new Permission(900000000000097102L, "FINANCE_ACCOUNTING_MANAGE", "FINANCE_ACCOUNTING", "MANAGE", "HIGH", false),
        new Permission(900000000000095101L, "COMMERCIAL_CONTRACT_READ", "COMMERCIAL_CONTRACT", "READ", "LOW", false),
        new Permission(900000000000095102L, "COMMERCIAL_CONTRACT_MANAGE", "COMMERCIAL_CONTRACT", "MANAGE", "HIGH", false),
        new Permission(900000000000095103L, "COMMERCIAL_PRICING_READ", "COMMERCIAL_PRICING", "READ", "LOW", false),
        new Permission(900000000000095104L, "COMMERCIAL_PRICING_MANAGE", "COMMERCIAL_PRICING", "MANAGE", "HIGH", false),
        new Permission(900000000000095105L, "COMMERCIAL_PRICING_APPROVE", "COMMERCIAL_PRICING", "APPROVE", "HIGH", false),
        new Permission(900000000000124101L, "COMMERCIAL_CATALOG_MANAGE", "COMMERCIAL_CATALOG", "MANAGE", "HIGH", false),
        new Permission(900000000000124102L, "COMMERCIAL_TEMPLATE_MANAGE", "COMMERCIAL_TEMPLATE", "MANAGE", "HIGH", false),
        new Permission(900000000000124103L, "COMMERCIAL_TEMPLATE_APPROVE", "COMMERCIAL_TEMPLATE", "APPROVE", "HIGH",
            false),
        new Permission(900000000000125101L, "CURRENCY_SPEC_READ", "CURRENCY_SPEC", "READ", "LOW", false),
        new Permission(900000000000125102L, "CURRENCY_SPEC_MANAGE", "CURRENCY_SPEC", "MANAGE", "HIGH", false),
        new Permission(900000000000127101L, "WORKFLOW_DEFINITION_READ", "WORKFLOW_DEFINITION", "READ", "LOW", false),
        new Permission(900000000000127102L, "WORKFLOW_DEFINITION_MANAGE", "WORKFLOW_DEFINITION", "MANAGE", "HIGH", false),
        new Permission(900000000000127103L, "WORKFLOW_ACTION_TEMPLATE_MANAGE", "WORKFLOW_ACTION_TEMPLATE", "MANAGE", "HIGH",
            false),
        new Permission(900000000000127104L, "WORKFLOW_INSTANCE_START", "WORKFLOW_INSTANCE", "START", "LOW", false),
        new Permission(900000000000127105L, "WORKFLOW_TASK_HANDLE", "WORKFLOW_TASK", "HANDLE", "LOW", false),
        new Permission(900000000000127106L, "WORKFLOW_INSTANCE_READ", "WORKFLOW_INSTANCE", "READ", "LOW", false),
        new Permission(900000000000127107L, "WORKFLOW_INSTANCE_MANAGE", "WORKFLOW_INSTANCE", "MANAGE", "HIGH", false),
        new Permission(900000000000130101L, "FILE_STORAGE_READ", "FILE_STORAGE", "READ", "LOW", false),
        new Permission(900000000000130102L, "FILE_STORAGE_MANAGE", "FILE_STORAGE", "MANAGE", "HIGH", false),
        new Permission(900000000000134101L, "FILE_RECORD_READ", "FILE_RECORD", "READ", "LOW", false),
        new Permission(900000000000134102L, "FILE_RECORD_DOWNLOAD", "FILE_RECORD", "DOWNLOAD", "HIGH", false),
        new Permission(900000000000131101L, "DATA_QUERY_READ", "DATA_QUERY", "READ", "LOW", true),
        new Permission(900000000000131102L, "DATA_SOURCE_MANAGE", "DATA_SOURCE", "MANAGE", "HIGH", false),
        new Permission(900000000000131103L, "DATA_QUERY_TEMPLATE_MANAGE", "DATA_QUERY_TEMPLATE", "MANAGE", "HIGH",
            false),
        new Permission(900000000000131104L, "DATA_QUERY_EXECUTE", "DATA_QUERY", "EXECUTE", "LOW", false),
        new Permission(900000000000135101L, "I18N_VIEW", "I18N", "VIEW", "LOW", true),
        new Permission(900000000000135102L, "I18N_EDIT", "I18N", "EDIT", "HIGH", false),
        new Permission(900000000000135103L, "I18N_PUBLISH", "I18N", "PUBLISH", "HIGH", false));

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

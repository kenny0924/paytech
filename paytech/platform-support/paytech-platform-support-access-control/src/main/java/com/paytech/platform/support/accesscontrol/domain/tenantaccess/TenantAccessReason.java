package com.paytech.platform.support.accesscontrol.domain.tenantaccess;



/** 当前授权的有序解释，不代表一次请求最终获准。 */
@lombok.AllArgsConstructor
@lombok.Getter
public enum TenantAccessReason implements com.paytech.foundation.common.enums.CodeEnum {
    TENANT_OWNER_INACTIVE("来源业务状态未允许访问"),

    TENANT_SECURITY_SUSPENDED("租户已安全暂停"),

    ACCOUNT_INACTIVE("账号待验证、已停用或已关闭"),

    MEMBER_INACTIVE("成员已暂停或撤销"),

    ROLE_INACTIVE("角色已停用"),

    GRANT_REVOKED("角色授予已撤销"),

    GRANT_NOT_STARTED("授权尚未生效"),

    GRANT_EXPIRED("授权已过期"),

    ROLE_PERMISSION_REVOKED("角色上的权限关联已撤销"),

    PERMISSION_INACTIVE("权限已停用");

    private final String message;

    /** 原有原因名称就是稳定 Wire Code。 */
    @Override
    public String getCode() { return name(); }

    /** 合并数据库标记，时间比较仍以本次查询的数据库时刻为准。 */
    public static java.util.List<TenantAccessReason> combine(java.util.List<TenantAccessReason> base,
        boolean roleInactive, boolean revoked, boolean notStarted, boolean expired,
        boolean permissionRevoked, boolean permissionInactive) {
        var reasons = new java.util.ArrayList<>(base);
        if (roleInactive) reasons.add(ROLE_INACTIVE);
        if (revoked) reasons.add(GRANT_REVOKED);
        if (notStarted) reasons.add(GRANT_NOT_STARTED);
        if (expired) reasons.add(GRANT_EXPIRED);
        if (permissionRevoked) reasons.add(ROLE_PERMISSION_REVOKED);
        if (permissionInactive) reasons.add(PERMISSION_INACTIVE);
        return java.util.List.copyOf(reasons);
    }
}

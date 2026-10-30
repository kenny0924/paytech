package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import lombok.Getter;
import lombok.Setter;

/**
 * 租户内具备某权限、可接收通知的成员（账号、成员、角色授予、角色权限、权限五表组合查询结果）
 * <li>只承载账号标识与登录邮箱的稳定 Vault Token，不含邮箱原值或脱敏值。</li>
 */
@Getter
@Setter
public class TenantPermissionRecipientView {

    /** 用户账号 ID。 */
    private Long accountId;

    /**
     * 已验证登录邮箱的 Vault Token
     * <li>EMAIL_ADDRESS_V1 STABLE；禁止日志输出。</li>
     */
    private String emailVaultToken;

    /** 不输出邮箱 Token。 */
    @Override
    public String toString() {
        return "TenantPermissionRecipientView[accountId=" + accountId + "]";
    }
}

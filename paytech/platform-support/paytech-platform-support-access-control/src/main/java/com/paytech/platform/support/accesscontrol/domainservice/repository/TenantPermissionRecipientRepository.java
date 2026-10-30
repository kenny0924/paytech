package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantPermissionRecipientView;

/** 租户内具备某权限的通知收件成员（租户、成员、账号、角色授予、角色、角色权限、权限）的只读持久化合同。 */
public interface TenantPermissionRecipientRepository {

    /**
     * 读取租户内经当前有效角色持有该权限、账号有效且邮箱已验证的成员
     *
     * @param tenantType 租户类型
     * @param ownerRefId 租户所有者引用
     * @param permissionCode 权限码
     * @param now 角色授予生效判断时刻
     * @param limit 最多返回行数
     * @return 按账号 ID 升序；租户不存在时为空
     */
    List<TenantPermissionRecipientView> findRecipients(AccessTenantType tenantType, long ownerRefId, String permissionCode,
        Instant now, int limit);
}

package com.paytech.platform.support.accesscontrol.domainservice.service;

import java.util.List;

import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantPermissionRecipientView;

/** 解析租户内具备某权限、可接收通知的成员；供通知受理组合使用，不返回持久化对象。 */
public interface TenantPermissionRecipientService {

    /**
     * 读取租户内具备该权限的通知收件成员
     * <li>成员 ACTIVE；角色授予 ACTIVE 且当前时刻在生效区间内；角色、角色权限与权限均 ACTIVE。</li>
     * <li>账号 ACTIVE 且登录邮箱已验证；不判断租户来源或安全状态。</li>
     *
     * @param tenantType 租户类型（CLIENT 或 PARTNER）
     * @param ownerRefId 租户所有者引用（Client ID 或 Local Partner ID）
     * @param permissionCode 权限码
     * @param limit 最多返回人数
     * @return 按账号 ID 升序；租户不存在或无人持有时为空
     */
    List<TenantPermissionRecipientView> findRecipients(AccessTenantType tenantType, long ownerRefId, String permissionCode,
        int limit);
}

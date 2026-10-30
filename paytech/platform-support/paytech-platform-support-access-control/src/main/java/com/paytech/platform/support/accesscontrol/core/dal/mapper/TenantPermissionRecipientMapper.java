package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantPermissionRecipientView;

/** 租户内具备某权限的通知收件成员只读 SQL；SQL 位于 Mapper XML。 */
@Mapper
public interface TenantPermissionRecipientMapper {

    /**
     * 按租户类型与所有者引用读取 ACTIVE 成员中经当前有效角色持有该 ACTIVE 权限、账号 ACTIVE 且邮箱已验证的账号，按账号 ID 升序
     *
     * @param tenantType 租户类型 Code
     * @param ownerRefId 租户所有者引用
     * @param permissionCode 权限码
     * @param now 角色授予生效判断时刻
     * @param limit 最多返回行数
     * @return 收件成员；租户不存在时为空
     */
    List<TenantPermissionRecipientView> selectRecipients(@Param("tenantType") String tenantType,
        @Param("ownerRefId") long ownerRefId, @Param("permissionCode") String permissionCode, @Param("now") Instant now,
        @Param("limit") int limit);
}

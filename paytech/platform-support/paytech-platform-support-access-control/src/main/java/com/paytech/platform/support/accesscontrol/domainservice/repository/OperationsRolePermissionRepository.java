package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;

/** 角色权限关联单表保存。 */
public interface OperationsRolePermissionRepository {
    /** 撤销角色全部有效权限。 */
    void revokeAll(long roleId, Instant now);

    /** 精确撤销本次移除的权限关联。 */
    void revoke(long roleId, long permissionId, Instant now);

    /** 创建或恢复指定权限关联。 */
    void grant(long id, long roleId, long permissionId, Instant now);
}

package com.paytech.platform.support.accesscontrol.domainservice.service;

import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ChangePermissionStatusContext;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.CreatePermissionContext;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.UpdatePermissionContext;

/** 权限本地保存能力，调用方负责授权、Context 及事务。 */
public interface PermissionManagementService {
    /** 创建固定 INACTIVE 的权限，不授予任何角色或成员。 */
    void create(CreatePermissionContext context);
    /** 保存已验证的风险等级变更。 */
    void update(UpdatePermissionContext context);
    /** 保存已验证的状态变更，保留所有关联。 */
    void changeStatus(ChangePermissionStatusContext context);
}

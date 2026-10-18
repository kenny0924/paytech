package com.paytech.platform.support.accesscontrol.domainservice.service;

import com.paytech.platform.support.accesscontrol.domain.operations.CreateOperationsRoleContext;
import com.paytech.platform.support.accesscontrol.domain.operations.DeleteOperationsRoleContext;
import com.paytech.platform.support.accesscontrol.domain.operations.UpdateOperationsRoleContext;

/** 接收已验证上下文，仅保存本地业务事实。 */
public interface OperationsRoleService {
    /** 在调用方本地事务内保存 CreateOperationsRoleContext。 */
    void createRole(CreateOperationsRoleContext context);

    /** 在调用方本地事务内保存 UpdateOperationsRoleContext。 */
    void updateRole(UpdateOperationsRoleContext context);

    /** 在调用方本地事务内保存 DeleteOperationsRoleContext。 */
    void deleteRole(DeleteOperationsRoleContext context);

}

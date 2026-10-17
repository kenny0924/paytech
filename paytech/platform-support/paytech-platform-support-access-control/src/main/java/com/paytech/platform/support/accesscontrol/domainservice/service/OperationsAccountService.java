package com.paytech.platform.support.accesscontrol.domainservice.service;

import com.paytech.platform.support.accesscontrol.domain.operations.AssignOperationsRolesContext;
import com.paytech.platform.support.accesscontrol.domain.operations.CreateOperationsAccountContext;
import com.paytech.platform.support.accesscontrol.domain.operations.DeleteOperationsAccountContext;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsAccountStateContext;

/** 接收已验证上下文，仅保存本地业务事实。 */
public interface OperationsAccountService {
    /** 在调用方本地事务内保存 CreateOperationsAccountContext。 */
    void createAccount(CreateOperationsAccountContext context);

    /** 在调用方本地事务内保存 OperationsAccountStateContext。 */
    void disableAccount(OperationsAccountStateContext context);

    /** 在调用方本地事务内保存 OperationsAccountStateContext。 */
    void enableAccount(OperationsAccountStateContext context);

    /** 在调用方本地事务内保存 DeleteOperationsAccountContext。 */
    void deleteAccount(DeleteOperationsAccountContext context);

    /** 在调用方本地事务内保存 AssignOperationsRolesContext。 */
    void assignRoles(AssignOperationsRolesContext context);

}

package com.paytech.platform.support.accesscontrol.domainservice.service;

import com.paytech.platform.support.accesscontrol.domain.tenantaccess.ChangeTenantSecurityStatusContext;

/** 租户安全状态的本地保存能力；授权及事务由所属 Processor 编排。 */
public interface TenantAccessManagementService {
    /** 只保存已确认的安全状态，不修改来源业务或成员授权事实。 */
    void changeSecurityStatus(ChangeTenantSecurityStatusContext context);
}

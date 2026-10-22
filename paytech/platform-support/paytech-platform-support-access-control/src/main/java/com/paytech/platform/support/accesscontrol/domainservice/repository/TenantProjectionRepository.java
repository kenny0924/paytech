package com.paytech.platform.support.accesscontrol.domainservice.repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionAccount;

/** 封装工作区来源投影的本地持久化能力。 */
public interface TenantProjectionRepository {
    /** 锁定申请人账号，始终先于 Tenant 和 Member 行锁。 */
    TenantProjectionAccount lockAccount(long id);
}

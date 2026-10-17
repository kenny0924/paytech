package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionGrant;

/** 来源投影中该表的本地持久化边界。 */
public interface TenantProjectionGrantRepository {

    /** 保存初始 OWNER 自身的角色授予。 */
    void insert(TenantProjectionGrant grant, Instant now);

    /** 撤销唯一有效入驻授予并保留历史，行数不符回滚。 */
    void revokeOnboarding(long tenantId, long memberId, Instant now);
}

package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjection;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;

/** 来源投影中该表的本地持久化边界。 */
public interface TenantProjectionTenantRepository {

    /** 读取来源当前投影。 */
    TenantProjection find(long ownerRefId);

    /** 锁定唯一 Client 来源的 Tenant。 */
    TenantProjection lock(long ownerRefId);

    /** 原子保存第一版 PENDING 来源。 */
    void insert(TenantProjection tenant, Instant now);

    /** 同时比较本地版本与来源版本；SQL 成功后同步原 Tenant。 */
    void update(TenantProjection tenant, int sourceVersion, String hash, OperationsTenantOwnerStatus status, Instant now);
}

package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantAccess;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantSecurityStatus;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantFilter;

/** 租户表的安全状态读取与条件更新边界。 */
public interface TenantAccessRepository {

    /** 读取指定租户安全字段。 */
    TenantAccess tenant(long tenantId);

    /** 在当前事务锁定目标租户行。 */
    TenantAccess lock(long tenantId);

    /** 按相同筛选稳定分页读取租户。 */
    List<TenantAccess> tenants(TenantFilter filter, long offset, int size);

    /** 按相同筛选统计租户数。 */
    long tenantCount(TenantFilter filter);

    /** 读取本次数据库 UTC 微秒时刻。 */
    Instant currentTime();

    /** CAS 成功后同步原租户对象；不接受整个请求上下文。 */
    void changeSecurityStatus(TenantAccess tenant, OperationsTenantSecurityStatus status, Instant now);
}

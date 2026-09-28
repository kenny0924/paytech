package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjection;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.TenantProjectionMapper;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionTenantRepository;

import lombok.AllArgsConstructor;

/** 复用本表 SQL，不开启事务或调用外部能力。 */
@Repository
@AllArgsConstructor
public class TenantProjectionTenantRepositoryImpl implements TenantProjectionTenantRepository {
    private final TenantProjectionMapper mapper;

    /** 读取来源当前投影。 */
    @Override
    public TenantProjection find(long ownerRefId) {
        return mapper.find(ownerRefId);
    }

    /** 锁定唯一 Client 来源的 Tenant。 */
    @Override
    public TenantProjection lock(long ownerRefId) {
        return mapper.lockTenant(ownerRefId);
    }

    /** 原子保存第一版 PENDING 来源。 */
    @Override
    public void insert(TenantProjection tenant, Instant now) {
        requireOne(mapper.insertTenant(tenant.tenantId(), tenant.ownerRefId(), tenant.sourcePayloadHash(), now));
    }

    /** 同时比较本地版本与来源版本；SQL 成功后同步原 Tenant。 */
    @Override
    public void update(TenantProjection tenant, int sourceVersion, String hash, OperationsTenantOwnerStatus status,
        Instant now) {
        if (tenant.version() == Integer.MAX_VALUE)
            throw new ConcurrentRequestException("租户投影已被并发更新，请重试原来源命令");
        requireOne(mapper.updateStatus(tenant.tenantId(), tenant.version(), tenant.sourceVersion(), sourceVersion, hash,
            status, now));
        tenant.ownerStatus(status);
        tenant.sourceVersion(sourceVersion);
        tenant.sourcePayloadHash(hash);
        tenant.version(tenant.version() + 1);
    }

    /** 写入必须准确影响一行，不将其他唯一键冲突解释为成功。 */
    private void requireOne(int rows) {
        if (rows != 1)
            throw new ConcurrentRequestException("租户投影已被并发更新，请重试原来源命令");
    }
}

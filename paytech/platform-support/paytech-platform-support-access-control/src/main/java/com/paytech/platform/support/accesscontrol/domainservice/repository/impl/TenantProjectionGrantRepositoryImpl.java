package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionGrant;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.TenantProjectionMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantProjectionGrantRepository;
import com.paytech.platform.support.api.enums.TenantProjectionErrorCode;

import lombok.AllArgsConstructor;

/** 复用本表 SQL，不开启事务或调用外部能力。 */
@Repository
@AllArgsConstructor
public class TenantProjectionGrantRepositoryImpl implements TenantProjectionGrantRepository {
    private final TenantProjectionMapper mapper;

    /** 保存初始 OWNER 自身的角色授予。 */
    @Override
    public void insert(TenantProjectionGrant grant, Instant now) {
        requireOne(mapper.insertGrant(grant.grantId(), grant.tenantId(), grant.memberId(), grant.roleId(), now));
    }

    /** 撤销唯一有效入驻授予并保留历史，行数不符回滚。 */
    @Override
    public void revokeOnboarding(long tenantId, long memberId, Instant now) {
        if (mapper.revokeOnboarding(tenantId, memberId, now) != 1)
            throw new FailException(TenantProjectionErrorCode.TENANT_PROJECTION_CONFLICT);
    }

    /** 写入必须准确影响一行，不将其他唯一键冲突解释为成功。 */
    private void requireOne(int rows) {
        if (rows != 1) throw new ConcurrentRequestException("租户投影已被并发更新，请重试原来源命令");
    }
}

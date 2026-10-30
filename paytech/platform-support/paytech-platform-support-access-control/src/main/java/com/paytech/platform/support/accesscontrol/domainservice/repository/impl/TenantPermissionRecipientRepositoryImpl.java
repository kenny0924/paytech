package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.mapper.TenantPermissionRecipientMapper;
import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantPermissionRecipientView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantPermissionRecipientRepository;

import lombok.AllArgsConstructor;

/** 只委托 Mapper，不开启事务。 */
@Repository
@AllArgsConstructor
public class TenantPermissionRecipientRepositoryImpl implements TenantPermissionRecipientRepository {
    private final TenantPermissionRecipientMapper mapper;

    /** 以租户类型 Code 查询。 */
    @Override
    public List<TenantPermissionRecipientView> findRecipients(AccessTenantType tenantType, long ownerRefId,
        String permissionCode, Instant now, int limit) {
        return mapper.selectRecipients(tenantType.getCode(), ownerRefId, permissionCode, now, limit);
    }
}

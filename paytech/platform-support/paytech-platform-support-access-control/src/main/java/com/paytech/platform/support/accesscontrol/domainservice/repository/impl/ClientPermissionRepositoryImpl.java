package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.mapper.ClientPermissionMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ClientPermissionRepository;

import lombok.AllArgsConstructor;

/** 只委托 Mapper，不开启事务。 */
@Repository
@AllArgsConstructor
public class ClientPermissionRepositoryImpl implements ClientPermissionRepository {
    private final ClientPermissionMapper mapper;

    /** 空集合不查询数据库。 */
    @Override
    public Set<String> granted(long memberId, long tenantId, Collection<String> codes, Instant now) {
        if (codes.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(mapper.granted(memberId, tenantId, codes, now));
    }
}

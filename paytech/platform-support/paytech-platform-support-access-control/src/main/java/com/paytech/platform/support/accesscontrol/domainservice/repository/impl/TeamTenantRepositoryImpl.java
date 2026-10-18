package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamTenant;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ClientTeamMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamTenantRepository;

import lombok.AllArgsConstructor;

/** CLIENT 租户状态的只读实现。 */
@Repository
@AllArgsConstructor
public class TeamTenantRepositoryImpl implements TeamTenantRepository {
    private final ClientTeamMapper mapper;

    /** 读取 CLIENT 租户；不存在为空。 */
    @Override
    public TeamTenant tenant(long tenantId) {
        return mapper.tenant(tenantId);
    }
}

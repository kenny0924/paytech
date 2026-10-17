package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamRole;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ClientTeamMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamRoleRepository;

import lombok.AllArgsConstructor;

/** 工作区租户系统角色的只读实现。 */
@Repository
@AllArgsConstructor
public class TeamRoleRepositoryImpl implements TeamRoleRepository {
    private final ClientTeamMapper mapper;

    /** 按代码读取本工作区系统角色；不存在为空。 */
    @Override
    public TeamRole systemRole(long tenantId, String roleCode) {
        return mapper.systemRole(tenantId, roleCode);
    }

    /** 按主键读取本工作区系统角色；不存在为空。 */
    @Override
    public TeamRole role(long tenantId, long roleId) {
        return mapper.role(tenantId, roleId);
    }
}

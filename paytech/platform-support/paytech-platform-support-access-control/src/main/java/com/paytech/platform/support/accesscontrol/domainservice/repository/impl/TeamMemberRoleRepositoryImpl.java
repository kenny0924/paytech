package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMemberGrant;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ClientTeamMapper;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantGrantStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamMemberRoleRepository;

import lombok.AllArgsConstructor;

/** 成员角色授予的团队管理持久化实现；不开启事务。 */
@Repository
@AllArgsConstructor
public class TeamMemberRoleRepositoryImpl implements TeamMemberRoleRepository {
    private final ClientTeamMapper mapper;

    /** 行锁读取成员当前全部 ACTIVE 授予。 */
    @Override
    public List<TeamMemberGrant> lockActive(long tenantId, long memberId) {
        return mapper.lockActiveGrants(tenantId, memberId);
    }

    /** 撤销一项授予。 */
    @Override
    public void revoke(TeamMemberGrant grant, Instant now) {
        requireOne(mapper.revokeGrant(grant.grantId(), grant.version(), now));
        grant.status(TenantGrantStatus.REVOKED);
        grant.version(grant.version() + 1);
    }

    /** 写入一项 ACTIVE 授予。 */
    @Override
    public void insert(TeamMemberGrant grant, Instant now) {
        requireOne(mapper.insertGrant(grant, now));
        grant.status(TenantGrantStatus.ACTIVE);
        grant.version(1);
    }

    /** 写入必须准确影响一行；失败时不修改内存对象。 */
    void requireOne(int rows) {
        if (rows != 1) {
            throw new ConcurrentRequestException("成员角色已被其他操作修改，请刷新后重试");
        }
    }
}

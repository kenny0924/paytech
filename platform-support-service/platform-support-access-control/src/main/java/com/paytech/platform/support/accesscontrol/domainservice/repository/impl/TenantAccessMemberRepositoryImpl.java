package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.mapper.TenantAccessManagementMapper;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.MemberFilter;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.TenantMemberView;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantAccessMemberRepository;

import lombok.AllArgsConstructor;

/** 以租户成员表为入口的账号关联查询，只读取本模块事实。 */
@Repository
@AllArgsConstructor
public class TenantAccessMemberRepositoryImpl implements TenantAccessMemberRepository {
    private final TenantAccessManagementMapper mapper;

    @Override
    public TenantMemberView member(long tenantId, long memberId) { return mapper.member(tenantId, memberId); }

    @Override
    public List<TenantMemberView> members(long tenantId, MemberFilter filter, long offset, int size) { return mapper.members(tenantId, filter, offset, size); }

    @Override
    public long memberCount(long tenantId, MemberFilter filter) { return mapper.memberCount(tenantId, filter); }

    @Override
    public long invalidMembers(long tenantId) { return mapper.invalidMembers(tenantId); }
}

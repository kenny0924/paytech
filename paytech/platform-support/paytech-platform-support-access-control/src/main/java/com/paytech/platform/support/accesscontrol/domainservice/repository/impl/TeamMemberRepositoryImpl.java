package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMember;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ClientTeamMapper;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TeamMemberView;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamMemberRepository;

import lombok.AllArgsConstructor;

/** CLIENT 工作区成员的团队管理持久化实现；不开启事务。 */
@Repository
@AllArgsConstructor
public class TeamMemberRepositoryImpl implements TeamMemberRepository {
    private final ClientTeamMapper mapper;

    /** 本工作区 ACTIVE 成员一页。 */
    @Override
    public List<TeamMemberView> members(long tenantId, Instant now, int offset, int size) {
        return mapper.members(tenantId, now, offset, size);
    }

    /** 本工作区 ACTIVE 成员总数。 */
    @Override
    public long countMembers(long tenantId) {
        return mapper.countMembers(tenantId);
    }

    /** 读取本工作区的成员；不存在为空。 */
    @Override
    public TeamMember member(long tenantId, long memberId) {
        return mapper.member(tenantId, memberId);
    }

    /** 行锁读取本工作区的成员；不存在为空。 */
    @Override
    public TeamMember lock(long tenantId, long memberId) {
        return mapper.lockMember(tenantId, memberId);
    }

    /** 读取账号在本工作区的成员；不存在为空。 */
    @Override
    public TeamMember memberByAccount(long tenantId, long accountId) {
        return mapper.memberByAccount(tenantId, accountId);
    }

    /** 行锁读取账号在本工作区的成员；不存在为空。 */
    @Override
    public TeamMember lockByAccount(long tenantId, long accountId) {
        return mapper.lockMemberByAccount(tenantId, accountId);
    }

    /** 受邀邮箱是否已是本工作区 ACTIVE 成员。 */
    @Override
    public boolean activeMemberExists(long tenantId, String emailToken) {
        return mapper.countActiveMembersByEmail(tenantId, emailToken) > 0;
    }

    /** 写入新 ACTIVE 成员；重复成员由唯一索引抛出。 */
    @Override
    public void insert(TeamMember member, Instant now) {
        requireOne(mapper.insertMember(member, now));
        member.status(OperationsMemberStatus.ACTIVE);
        member.version(1);
        member.createTime(now);
    }

    /** ACTIVE 成员改类型。 */
    @Override
    public void changeType(TeamMember member, TenantMemberType memberType, Instant now) {
        requireOne(mapper.changeMemberType(member.memberId(), member.version(), memberType, now));
        member.memberType(memberType);
        member.version(member.version() + 1);
    }

    /** ACTIVE 成员置为 REVOKED。 */
    @Override
    public void revoke(TeamMember member, Instant now) {
        requireOne(mapper.revokeMember(member.memberId(), member.version(), now));
        member.status(OperationsMemberStatus.REVOKED);
        member.version(member.version() + 1);
    }

    /** REVOKED 成员恢复为 ACTIVE。 */
    @Override
    public void restore(TeamMember member, TenantMemberType memberType, long invitedBy, Instant now) {
        requireOne(mapper.restoreMember(member.memberId(), member.version(), memberType, invitedBy, now));
        member.status(OperationsMemberStatus.ACTIVE);
        member.memberType(memberType);
        member.invitedBy(invitedBy);
        member.version(member.version() + 1);
    }

    /** 写入必须准确影响一行；失败时不修改内存对象。 */
    void requireOne(int rows) {
        if (rows != 1) {
            throw new ConcurrentRequestException("成员已被其他操作修改，请刷新后重试");
        }
    }
}

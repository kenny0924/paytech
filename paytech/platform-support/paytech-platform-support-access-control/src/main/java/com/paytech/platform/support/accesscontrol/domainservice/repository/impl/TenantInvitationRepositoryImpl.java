package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantInvitation;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ClientTeamMapper;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationView;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantInvitationStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantInvitationRepository;

import lombok.AllArgsConstructor;

/** 租户成员邀请的单表持久化实现；不开启事务。 */
@Repository
@AllArgsConstructor
public class TenantInvitationRepositoryImpl implements TenantInvitationRepository {
    private final ClientTeamMapper mapper;

    /** 本工作区邀请一页，status 为显示状态代码。 */
    @Override
    public List<TenantInvitationView> invitations(long tenantId, String status, Instant now, int offset, int size) {
        return mapper.invitations(tenantId, status, now, offset, size);
    }

    /** 与列表相同条件的总数。 */
    @Override
    public long countInvitations(long tenantId, String status, Instant now) {
        return mapper.countInvitations(tenantId, status, now);
    }

    /** 本工作区未到期的 PENDING 邀请数。 */
    @Override
    public int countPending(long tenantId, Instant now) {
        return mapper.countPendingInvitations(tenantId, now);
    }

    /** 读取本工作区的邀请；不存在为空。 */
    @Override
    public TenantInvitation invitation(long tenantId, long invitationId) {
        return mapper.invitation(tenantId, invitationId);
    }

    /** 按主键读取邀请；不存在为空。 */
    @Override
    public TenantInvitation invitation(long invitationId) {
        return mapper.invitationById(invitationId);
    }

    /** 行锁读取邀请；不存在为空。 */
    @Override
    public TenantInvitation lock(long invitationId) {
        return mapper.lockInvitation(invitationId);
    }

    /** 行锁读取同租户同邮箱占用活动位的邀请；不存在为空。 */
    @Override
    public TenantInvitation lockActive(long tenantId, String emailToken) {
        return mapper.lockActiveInvitation(tenantId, emailToken);
    }

    /** 写入新 PENDING 邀请；活动位冲突由唯一索引抛出。 */
    @Override
    public void insert(TenantInvitation invitation, Instant now) {
        requireOne(mapper.insertInvitation(invitation, now));
        invitation.status(TenantInvitationStatus.PENDING);
        invitation.activeSlot(1);
        invitation.version(1);
        invitation.createTime(now);
    }

    /** 已到期的 PENDING 邀请置为 EXPIRED 并释放活动位。 */
    @Override
    public void expire(TenantInvitation invitation, Instant now) {
        requireOne(mapper.expireInvitation(invitation.invitationId(), invitation.version(), now));
        invitation.status(TenantInvitationStatus.EXPIRED);
        invitation.activeSlot(null);
        invitation.version(invitation.version() + 1);
    }

    /** 重新计算到期时间。 */
    @Override
    public void renew(TenantInvitation invitation, Instant expiresTime, Instant now) {
        requireOne(mapper.renewInvitation(invitation.invitationId(), invitation.version(), expiresTime, now));
        invitation.expiresTime(expiresTime);
        invitation.version(invitation.version() + 1);
    }

    /** 撤销 PENDING 邀请并释放活动位。 */
    @Override
    public void revoke(TenantInvitation invitation, Instant now) {
        requireOne(mapper.revokeInvitation(invitation.invitationId(), invitation.version(), now));
        invitation.status(TenantInvitationStatus.REVOKED);
        invitation.activeSlot(null);
        invitation.version(invitation.version() + 1);
    }

    /** 未到期的 PENDING 邀请完成接受并释放活动位。 */
    @Override
    public void accept(TenantInvitation invitation, long accountId, long memberId, Instant now) {
        requireOne(mapper.acceptInvitation(invitation.invitationId(), invitation.version(), accountId, memberId, now));
        invitation.status(TenantInvitationStatus.ACCEPTED);
        invitation.activeSlot(null);
        invitation.acceptedAccountId(accountId);
        invitation.acceptedMemberId(memberId);
        invitation.acceptedTime(now);
        invitation.version(invitation.version() + 1);
    }

    /** 写入必须准确影响一行；失败时不修改内存对象。 */
    void requireOne(int rows) {
        if (rows != 1) {
            throw new ConcurrentRequestException("邀请已被其他操作修改，请刷新后重试");
        }
    }
}

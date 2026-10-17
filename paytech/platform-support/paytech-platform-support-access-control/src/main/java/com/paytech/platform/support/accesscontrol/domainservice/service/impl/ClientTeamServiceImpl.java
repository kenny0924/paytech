package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.InvitationChallenge;
import com.paytech.platform.support.accesscontrol.domain.clientteam.AcceptTenantInvitationContext;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TeamMemberChange;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationChange;
import com.paytech.platform.support.accesscontrol.domainservice.repository.InvitationChallengeRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamMemberRoleRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantInvitationRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientTeamService;

import lombok.AllArgsConstructor;

/** 只保存 Processor 已在本事务内锁定并核对的团队事实；不开启事务，不调用外部依赖。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ClientTeamServiceImpl implements ClientTeamService {
    private final TeamMemberRepository members;
    private final TeamMemberRoleRepository grants;
    private final TenantInvitationRepository invitations;
    private final InvitationChallengeRepository challenges;

    /** 先释放旧活动位，再写邀请与挑战。 */
    @Override
    public void createInvitation(TenantInvitationChange change) {
        if (change.getExpiredInvitation() != null) {
            invitations.expire(change.getExpiredInvitation(), change.getNow());
        }
        invitations.insert(change.getInvitation(), change.getNow());
        challenges.insert(change.getChallenge());
    }

    /** 同一邀请只有一条活动挑战：先替代旧挑战再写新挑战。 */
    @Override
    public void resendInvitation(TenantInvitationChange change) {
        if (change.getPreviousChallenge() != null) {
            challenges.supersede(change.getPreviousChallenge(), change.getNow());
        }
        invitations.renew(change.getInvitation(), change.getExpiresTime(), change.getNow());
        challenges.insert(change.getChallenge());
    }

    /** 邀请撤销后链接同时失效。 */
    @Override
    public void revokeInvitation(TenantInvitationChange change) {
        if (change.getPreviousChallenge() != null) {
            challenges.revoke(change.getPreviousChallenge(), change.getNow());
        }
        invitations.revoke(change.getInvitation(), change.getNow());
    }

    /** 每名成员只持有一个工作区角色：撤销全部当前授予后写入新授予。 */
    @Override
    public void changeMemberRole(TeamMemberChange change) {
        change.getRevokedGrants().forEach(grant -> grants.revoke(grant, change.getNow()));
        grants.insert(change.getNewGrant(), change.getNow());
        if (change.getMember().memberType() != change.getMemberType()) {
            members.changeType(change.getMember(), change.getMemberType(), change.getNow());
        }
    }

    /** 撤销授予并移除成员，下一次请求的权限判断即失败关闭。 */
    @Override
    public void removeMember(TeamMemberChange change) {
        change.getRevokedGrants().forEach(grant -> grants.revoke(grant, change.getNow()));
        members.revoke(change.getMember(), change.getNow());
    }

    /** 成员、授予、挑战与邀请同一事务提交；任一条件更新失败整体回滚。 */
    @Override
    public void acceptInvitation(AcceptTenantInvitationContext context) {
        var invitation = context.getInvitation();
        var member = context.getMember();
        if (context.isNewMember()) {
            members.insert(member, context.getNow());
        }
        if (context.isRestore()) {
            context.getRevokedGrants().forEach(grant -> grants.revoke(grant, context.getNow()));
            members.restore(member, invitation.memberType(), invitation.invitedByMemberId(), context.getNow());
        }
        if (context.getNewGrant() != null) {
            grants.insert(context.getNewGrant(), context.getNow());
        }
        challenges.consume(context.getChallenge(), context.getNow());
        invitations.accept(invitation, member.accountId(), member.memberId(), context.getNow());
    }

    /** 失败尝试单独提交，到期或用尽时关闭挑战。 */
    @Override
    public void failProof(InvitationChallenge challenge, Instant now) {
        challenges.fail(challenge, now);
    }
}

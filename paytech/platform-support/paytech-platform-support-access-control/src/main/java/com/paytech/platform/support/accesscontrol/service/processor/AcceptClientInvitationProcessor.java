package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.InvitationChallenge;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMemberGrant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamRole;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamTenant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantInvitation;
import com.paytech.platform.support.accesscontrol.domain.clientteam.AcceptTenantInvitationContext;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientInvitationAcceptance;
import com.paytech.platform.support.accesscontrol.domain.clientteam.InvitationLinks.InvitationProof;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantInvitationStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantRoleStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.CustomerAccountRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.InvitationChallengeRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamMemberRoleRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamRoleRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamTenantRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantInvitationRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientTeamService;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientInvitationRequest;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.api.enums.ClientTeamErrorCode;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/**
 * 接受邀请（账号或租户上下文会话均可，按当前账号接受）
 * <li>锁 client-invitation:{invitationId} + customer:account:{accountId}，账号已有该工作区成员时再锁
 * tenant-member:{tenantId}:{memberId}；锁前只读解析邀请与成员。账号锁使同一账号的成员创建串行。</li>
 * <li>一个写事务按 账号 → 挑战 → 邀请 → 成员/授予 顺序行锁：创建或恢复成员、授予初始角色、CAS 消费挑战、完成邀请；
 * uk_tenant_member 兜底防重复成员。租户状态在事务内读取但不加锁。</li>
 * <li>结果：JOINED；本账号已是 ACTIVE 成员或同一账号重复接受为 ALREADY_MEMBER（重复接受不再审计）。
 * 已过期、已撤销或被其他账号接受 409 CLIENT_INVITATION_CLOSED；邮箱不一致 403；工作区不可用 403。</li>
 * <li>秘密错误先在短事务内记一次失败尝试，再返回 404 CLIENT_INVITATION_INVALID。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class AcceptClientInvitationProcessor
    extends AbstractLockProcessor<ClientInvitationRequest, ClientInvitationAcceptance> {
    private final TeamRoleRepository roles;
    private final OperationsAuditService audit;
    private final ClientTeamService service;
    private final TeamTenantRepository tenants;
    private final TeamMemberRepository members;
    private final TeamMemberRoleRepository grants;
    private final ClientWorkspaceAccessSupport access;
    private final CustomerAccountRepository accounts;
    private final TenantInvitationRepository invitations;
    private final ClientInvitationPreparation preparation;
    private final InvitationChallengeRepository challenges;
    private final CustomerAuthenticationService authentication;

    @Override
    protected ClientInvitationAcceptance processRequest(ClientInvitationRequest request) {
        return super.processRequest(request.withProof(preparation.prove(request.getChallengeNo(), request.getSecret())));
    }

    @Override
    protected LockCommand lockCommand(ClientInvitationRequest request) {
        long invitationId = request.getProof().invitationId();
        var keys = new ArrayList<>(List.of(
            ClientInvitationPreparation.fullLockKey(ClientInvitationPreparation.invitationLock(invitationId)),
            ClientInvitationPreparation.fullLockKey(ClientInvitationPreparation.accountLock(request.getAccountId()))));
        var invitation = invitations.invitation(invitationId);
        var member = invitation == null ? null : members.memberByAccount(invitation.tenantId(), request.getAccountId());
        if (member != null) {
            keys.add(ClientInvitationPreparation.fullLockKey(
                ClientInvitationPreparation.memberLock(member.tenantId(), member.memberId())));
        }
        return LockCommand.tryLocks(keys, preparation.lockWait(), preparation.lockLease());
    }

    @Override
    protected ClientInvitationAcceptance processWithinLock(ClientInvitationRequest request) {
        var proof = request.getProof();
        if (!proof.secretMatches()) {
            recordFailedProof(proof);
            throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_INVALID);
        }
        try {
            return access.writeTransaction().execute(status -> accept(request, proof));
        } catch (DuplicateKeyException | ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
    }

    /** 事务内重证并一次保存；重复接受直接返回原结果。 */
    ClientInvitationAcceptance accept(ClientInvitationRequest request, InvitationProof proof) {
        audit.observeTransaction();
        long accountId = request.getAccountId();
        authentication.currentContext(accountId, request.getSessionId());
        var account = accounts.lockAccount(accountId);
        var challenge = challenges.lock(proof.challengeId());
        var invitation = invitations.lock(proof.invitationId());
        var now = preparation.now();
        if (invitation == null || challenge == null || challenge.invitationId() != invitation.invitationId()) {
            throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_INVALID);
        }
        var replay = acceptedByThisAccount(invitation, accountId);
        if (replay != null) {
            audit.suppressReplay();
            return replay;
        }
        requireAcceptable(invitation, challenge, account, now);
        var tenant = requireTenant(invitation.tenantId());
        var role = requireRole(invitation);
        var context = context(invitation, challenge, members.lockByAccount(invitation.tenantId(), accountId), account,
            role, now);
        service.acceptInvitation(context);
        String outcome = context.joins() ? ClientInvitationAcceptance.JOINED
            : ClientInvitationAcceptance.ALREADY_MEMBER;
        audit.record(() -> Outcome.succeeded(Long.toString(accountId), invitation.tenantId(),
            new Target("TENANT_INVITATION", Long.toString(invitation.invitationId())), "INVITATION_" + outcome, now));
        return new ClientInvitationAcceptance(outcome, invitation.tenantId(), context.getMember().memberId(),
            tenant.clientId());
    }

    /** 已被本账号接受时返回原结果；被其他账号接受返回 409。 */
    ClientInvitationAcceptance acceptedByThisAccount(TenantInvitation invitation, long accountId) {
        if (invitation.status() != TenantInvitationStatus.ACCEPTED) {
            return null;
        }
        if (!Objects.equals(invitation.acceptedAccountId(), accountId) || invitation.acceptedMemberId() == null) {
            throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_CLOSED);
        }
        var tenant = tenants.tenant(invitation.tenantId());
        return new ClientInvitationAcceptance(ClientInvitationAcceptance.ALREADY_MEMBER, invitation.tenantId(),
            invitation.acceptedMemberId(), tenant == null ? 0 : tenant.clientId());
    }

    /** 邀请必须是未到期的 PENDING、挑战仍可消费、账号邮箱等于受邀邮箱。 */
    void requireAcceptable(TenantInvitation invitation, InvitationChallenge challenge, CustomerAccount account,
        java.time.Instant now) {
        if (invitation.status() != TenantInvitationStatus.PENDING || !invitation.expiresTime().isAfter(now)) {
            throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_CLOSED);
        }
        if (challenge.status() != ClientChallengeStatus.PENDING || !challenge.expiresTime().isAfter(now)) {
            throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_INVALID);
        }
        if (account == null || !Objects.equals(account.emailToken(), invitation.invitedEmailToken())) {
            throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_EMAIL_MISMATCH);
        }
    }

    /** 工作区来源与安全状态均为 ACTIVE 才可加入。 */
    TeamTenant requireTenant(long tenantId) {
        var tenant = tenants.tenant(tenantId);
        if (tenant == null || !tenant.accessible()) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE);
        }
        return tenant;
    }

    /** 初始角色必须仍是本工作区 ACTIVE 系统角色。 */
    TeamRole requireRole(TenantInvitation invitation) {
        var role = roles.role(invitation.tenantId(), invitation.initialRoleId());
        if (role == null || role.status() != TenantRoleStatus.ACTIVE) {
            throw new BizCheckFailException("邀请的初始角色已停用，请联系工作区管理员重新邀请");
        }
        return role;
    }

    /** 按成员现状一次构造保存上下文：新建、恢复或已是成员。 */
    AcceptTenantInvitationContext context(TenantInvitation invitation, InvitationChallenge challenge,
        TeamMember member, CustomerAccount account, TeamRole role, java.time.Instant now) {
        if (member != null && member.status() == OperationsMemberStatus.ACTIVE) {
            return new AcceptTenantInvitationContext(invitation, challenge, member, false, false, List.of(), null, now);
        }
        if (member != null && member.status() != OperationsMemberStatus.REVOKED) {
            throw new FailException(ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE);
        }
        boolean restore = member != null;
        if (!restore) {
            member = new TeamMember(preparation.nextId(), invitation.tenantId(), account.id(), invitation.memberType(),
                OperationsMemberStatus.ACTIVE, invitation.invitedByMemberId(), account.emailMasked(), 0, now);
        }
        var revoked = restore ? grants.lockActive(invitation.tenantId(), member.memberId()) : List.<TeamMemberGrant>of();
        var grant = new TeamMemberGrant(preparation.nextId(), member.memberId(), invitation.tenantId(), role.roleId(),
            role.roleCode(), now, invitation.invitedByMemberId(), null, 0);
        return new AcceptTenantInvitationContext(invitation, challenge, member, !restore, restore, revoked, grant, now);
    }

    /** 秘密错误：仍 PENDING 的挑战记一次失败（到期或满 5 次即关闭），单独提交。 */
    void recordFailedProof(InvitationProof proof) {
        access.writeTransaction().executeWithoutResult(status -> {
            var challenge = challenges.lock(proof.challengeId());
            if (challenge != null && challenge.status() == ClientChallengeStatus.PENDING) {
                service.failProof(challenge, preparation.now());
            }
        });
    }
}

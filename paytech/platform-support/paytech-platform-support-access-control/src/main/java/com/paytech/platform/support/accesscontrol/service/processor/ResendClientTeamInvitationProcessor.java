package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantInvitation;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamPermissions;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamRules;
import com.paytech.platform.support.accesscontrol.domain.clientteam.InvitationLinks.IssuedLink;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationChange;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationResult;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantInvitationStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.InvitationChallengeRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamRoleRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantInvitationRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientTeamService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientTeamRequest;
import com.paytech.platform.support.api.enums.ClientTeamErrorCode;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/**
 * 重发邀请（需要 CLIENT_TEAM_MANAGE）：同一邀请重新计算 7 天到期，新挑战替代旧挑战
 * <li>锁 client-invitation:{invitationId}，与撤销、接受同一互斥域；事务内再以挑战 → 邀请顺序行锁并按状态 CAS。</li>
 * <li>锁内、事务前只读确认归属与状态，再 Reveal 邮箱、签发链接；已接受或已撤销返回 409 CLIENT_INVITATION_CLOSED，
 * 已到期的 PENDING 邀请可以重发。</li>
 * <li>提交后同步发信，失败返回 emailDelivery=FAILED。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ResendClientTeamInvitationProcessor
    extends AbstractLockProcessor<ClientTeamRequest, TenantInvitationResult> {
    private final TeamRoleRepository roles;
    private final ClientTeamSupport support;
    private final OperationsAuditService audit;
    private final ClientTeamService service;
    private final ClientWorkspaceAccessSupport access;
    private final TenantInvitationRepository invitations;
    private final ClientInvitationPreparation preparation;
    private final InvitationChallengeRepository challenges;

    @Override
    protected LockCommand lockCommand(ClientTeamRequest request) {
        return LockCommand.tryLock(ClientInvitationPreparation.invitationLock(request.getTargetId()),
            Math.toIntExact(preparation.lockWait().toMillis()), Math.toIntExact(preparation.lockLease().toMillis()));
    }

    @Override
    protected TenantInvitationResult processWithinLock(ClientTeamRequest request) {
        // 事务外确认归属并准备邮箱与新链接
        var current = access.readTransaction().execute(status -> {
            var workspace = access.require(request.getAccountId(), request.getSessionId(),
                ClientTeamPermissions.TEAM_MANAGE);
            return requirePending(invitations.invitation(workspace.getTenantId(), request.getTargetId()));
        });
        String canonical = preparation.reveal(current.invitedEmailToken());
        var issued = preparation.issue(current.invitationId());

        // 本地原子写入
        var saved = save(request, issued);

        // 提交后发信
        return new TenantInvitationResult(saved.getInvitation(), saved.getAsOf(),
            preparation.deliver(canonical, issued.link()));
    }

    /** 事务内按挑战 → 邀请顺序锁定并重证，替代旧挑战、续期并写入新挑战。 */
    TenantInvitationResult save(ClientTeamRequest request, IssuedLink issued) {
        try {
            return access.writeTransaction().execute(status -> {
                audit.observeTransaction();
                var workspace = access.require(request.getAccountId(), request.getSessionId(),
                    ClientTeamPermissions.TEAM_MANAGE);
                var now = preparation.now();
                var previousChallenge = challenges.lockActive(request.getTargetId());
                var invitation = invitations.lock(request.getTargetId());
                if (invitation == null || invitation.tenantId() != workspace.getTenantId()) {
                    throw new FailException(CommonErrorCode.NOT_FOUND);
                }
                requirePending(invitation);
                var expires = now.plus(ClientTeamRules.INVITATION_VALIDITY);
                var challenge = preparation.newChallenge(issued, invitation.invitationId(),
                    invitation.invitedEmailToken(), invitation.invitedEmailMasked(), expires,
                    previousChallenge == null ? null : previousChallenge.id(), now);
                service.resendInvitation(new TenantInvitationChange(invitation, null, challenge, previousChallenge,
                    expires, now));
                audit.record(() -> Outcome.succeeded(Long.toString(workspace.getAccountId()), workspace.getTenantId(),
                    new Target("TENANT_INVITATION", Long.toString(invitation.invitationId())), "INVITATION_RESENT",
                    now));
                var role = roles.role(invitation.tenantId(), invitation.initialRoleId());
                return new TenantInvitationResult(support.invitationView(invitation, role == null ? null
                    : role.roleCode(), support.emailMasked(invitation.tenantId(), invitation.invitedByMemberId())),
                    now, null);
            });
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
    }

    /** 不存在返回 404；只有 PENDING（含已到期）可以重发，其余 409。 */
    TenantInvitation requirePending(TenantInvitation invitation) {
        if (invitation == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        if (invitation.status() != TenantInvitationStatus.PENDING) {
            throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_CLOSED);
        }
        return invitation;
    }
}

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
 * 撤销邀请（需要 CLIENT_TEAM_MANAGE）：邀请与其活动挑战一并 REVOKED，链接立即失效
 * <li>锁 client-invitation:{invitationId}；事务内以挑战 → 邀请顺序行锁并按状态 CAS。</li>
 * <li>不属于本工作区 404；已撤销返回原结果且不重复审计；已接受或已终止 409 CLIENT_INVITATION_CLOSED。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class RevokeClientTeamInvitationProcessor
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
        try {
            return access.writeTransaction().execute(status -> {
                audit.observeTransaction();
                var workspace = access.require(request.getAccountId(), request.getSessionId(),
                    ClientTeamPermissions.TEAM_MANAGE);
                var now = preparation.now();
                var activeChallenge = challenges.lockActive(request.getTargetId());
                var invitation = invitations.lock(request.getTargetId());
                if (invitation == null || invitation.tenantId() != workspace.getTenantId()) {
                    throw new FailException(CommonErrorCode.NOT_FOUND);
                }
                if (invitation.status() == TenantInvitationStatus.REVOKED) {
                    audit.suppressReplay();
                    return result(invitation, now);
                }
                if (invitation.status() != TenantInvitationStatus.PENDING) {
                    throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_CLOSED);
                }
                service.revokeInvitation(new TenantInvitationChange(invitation, null, null, activeChallenge, null, now));
                audit.record(() -> Outcome.succeeded(Long.toString(workspace.getAccountId()), workspace.getTenantId(),
                    new Target("TENANT_INVITATION", Long.toString(invitation.invitationId())), "INVITATION_REVOKED",
                    now));
                return result(invitation, now);
            });
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
    }

    /** 由已锁定的邀请组装结果。 */
    TenantInvitationResult result(TenantInvitation invitation, java.time.Instant now) {
        var role = roles.role(invitation.tenantId(), invitation.initialRoleId());
        return new TenantInvitationResult(support.invitationView(invitation, role == null ? null : role.roleCode(),
            support.emailMasked(invitation.tenantId(), invitation.invitedByMemberId())), now, null);
    }
}

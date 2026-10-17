package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientInvitationView;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamRules;
import com.paytech.platform.support.accesscontrol.domain.clientteam.InvitationLinks.InvitationProof;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.CustomerAccountRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.InvitationChallengeRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamRoleRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamTenantRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantInvitationRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientTeamService;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerAuthenticationService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientInvitationRequest;
import com.paytech.platform.support.api.enums.ClientTeamErrorCode;

import lombok.AllArgsConstructor;

/**
 * 凭邀请链接查看邀请：公开预览（accountId 为空）或已登录详情；不消费挑战
 * <li>事务外核对链接秘密；锁 client-invitation:{invitationId} 后，秘密错误在短事务内记一次失败尝试再返回 404。</li>
 * <li>预览只返回脱敏邮箱、角色、显示状态与到期；详情另外重证会话，邮箱一致时附 Client，供 BFF 读取工作区名称。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ViewClientInvitationProcessor extends AbstractLockProcessor<ClientInvitationRequest, ClientInvitationView> {
    private final TeamRoleRepository roles;
    private final ClientTeamService service;
    private final TeamTenantRepository tenants;
    private final ClientWorkspaceAccessSupport access;
    private final CustomerAccountRepository accounts;
    private final TenantInvitationRepository invitations;
    private final ClientInvitationPreparation preparation;
    private final InvitationChallengeRepository challenges;
    private final CustomerAuthenticationService authentication;

    @Override
    protected ClientInvitationView processRequest(ClientInvitationRequest request) {
        return super.processRequest(request.withProof(preparation.prove(request.getChallengeNo(), request.getSecret())));
    }

    @Override
    protected LockCommand lockCommand(ClientInvitationRequest request) {
        return LockCommand.tryLock(ClientInvitationPreparation.invitationLock(request.getProof().invitationId()),
            Math.toIntExact(preparation.lockWait().toMillis()), Math.toIntExact(preparation.lockLease().toMillis()));
    }

    @Override
    protected ClientInvitationView processWithinLock(ClientInvitationRequest request) {
        var proof = request.getProof();
        if (!proof.secretMatches()) {
            recordFailedProof(proof);
            throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_INVALID);
        }
        return access.readTransaction().execute(status -> view(request, proof));
    }

    /** 同一只读快照内读取会话、邀请、角色与租户。 */
    ClientInvitationView view(ClientInvitationRequest request, InvitationProof proof) {
        String accountToken = null;
        if (request.getAccountId() != null) {
            authentication.currentContext(request.getAccountId(), request.getSessionId());
            accountToken = accounts.account(request.getAccountId()).emailToken();
        }
        var invitation = invitations.invitation(proof.invitationId());
        if (invitation == null) {
            throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_INVALID);
        }
        var now = preparation.now();
        var role = roles.role(invitation.tenantId(), invitation.initialRoleId());
        String status = ClientTeamRules.displayStatus(invitation, now).getCode();
        if (request.getAccountId() == null) {
            return new ClientInvitationView(invitation.invitedEmailMasked(), role == null ? null : role.roleCode(),
                status, invitation.expiresTime(), null, null);
        }
        boolean matches = Objects.equals(accountToken, invitation.invitedEmailToken());
        var tenant = matches ? tenants.tenant(invitation.tenantId()) : null;
        return new ClientInvitationView(invitation.invitedEmailMasked(), role == null ? null : role.roleCode(), status,
            invitation.expiresTime(), matches, tenant == null ? null : tenant.clientId());
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

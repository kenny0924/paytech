package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantInvitation;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamPermissions;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamRules;
import com.paytech.platform.support.accesscontrol.domain.clientteam.InvitationLinks.InvitationEmail;
import com.paytech.platform.support.accesscontrol.domain.clientteam.InvitationLinks.IssuedLink;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationChange;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationResult;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TenantInvitationRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientTeamService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientTeamRequest;
import com.paytech.platform.support.api.enums.ClientTeamErrorCode;
import com.paytech.platform.support.api.enums.ClientTeamRole;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/**
 * 发起工作区邀请（需要 CLIENT_TEAM_MANAGE）
 * <li>不加资源锁：同租户同邮箱的并发由 uk_tenant_invite_active 裁决，冲突返回 409 CLIENT_INVITATION_DUPLICATE；
 * 20 条上限在事务内计数，极端并发下可能多出一条，不影响安全。</li>
 * <li>事务外：邮箱规范化、权限预检、Vault STABLE Token、链接与 HMAC；事务内：重证会话与权限、已是成员与重复检查、
 * 释放已到期旧邀请、写入邀请与挑战、审计。</li>
 * <li>提交后同步发信；失败不回滚，响应 emailDelivery=FAILED，可重发。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class CreateClientTeamInvitationProcessor extends AbstractProcessor<ClientTeamRequest, TenantInvitationResult> {
    private final ClientTeamSupport support;
    private final OperationsAuditService audit;
    private final ClientTeamService service;
    private final TeamMemberRepository members;
    private final ClientWorkspaceAccessSupport access;
    private final TenantInvitationRepository invitations;
    private final ClientInvitationPreparation preparation;

    /** 角色只依赖输入，查询前校验。 */
    @Override
    protected void validate(ClientTeamRequest request) {
        ClientTeamRules.assignableRole(request.getRoleCode());
    }

    @Override
    protected TenantInvitationResult processRequest(ClientTeamRequest request) {
        // 事务外校验输入、预检权限并准备邮箱与链接
        var role = ClientTeamRules.assignableRole(request.getRoleCode());
        String canonical = preparation.canonicalEmail(request.getEmail());
        access.readTransaction().execute(status -> access.require(request.getAccountId(), request.getSessionId(),
            ClientTeamPermissions.TEAM_MANAGE));
        var email = preparation.protect(canonical);
        long invitationId = preparation.nextId();
        var issued = preparation.issue(invitationId);

        // 本地原子写入
        var saved = save(request, role, email, invitationId, issued);

        // 提交后发信
        return new TenantInvitationResult(saved.getInvitation(), saved.getAsOf(),
            preparation.deliver(email.canonical(), issued.link()));
    }

    /** 一个写事务内重证并保存；活动位冲突转为重复邀请，其他并发冲突返回 409。 */
    TenantInvitationResult save(ClientTeamRequest request, ClientTeamRole role, InvitationEmail email,
        long invitationId, IssuedLink issued) {
        try {
            return access.writeTransaction().execute(status -> {
                audit.observeTransaction();
                var workspace = access.require(request.getAccountId(), request.getSessionId(),
                    ClientTeamPermissions.TEAM_MANAGE);
                long tenantId = workspace.getTenantId();
                var now = preparation.now();
                var initialRole = support.requireRole(tenantId, role);
                if (members.activeMemberExists(tenantId, email.token())) {
                    throw new FailException(ClientTeamErrorCode.CLIENT_MEMBER_EXISTS);
                }
                var previous = invitations.lockActive(tenantId, email.token());
                if (previous != null && previous.expiresTime().isAfter(now)) {
                    throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_DUPLICATE);
                }
                if (invitations.countPending(tenantId, now) >= ClientTeamRules.PENDING_LIMIT) {
                    throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_LIMIT_REACHED);
                }
                var invitation = newInvitation(invitationId, tenantId, email, role, initialRole.roleId(),
                    workspace.getMemberId(), now);
                var challenge = preparation.newChallenge(issued, invitationId, email.token(), email.masked(),
                    invitation.expiresTime(), null, now);
                service.createInvitation(new TenantInvitationChange(invitation, previous, challenge, null, null, now));
                audit.record(() -> Outcome.succeeded(Long.toString(workspace.getAccountId()), tenantId,
                    new Target("TENANT_INVITATION", Long.toString(invitationId)), "INVITATION_CREATED", now));
                return new TenantInvitationResult(support.invitationView(invitation, role.getCode(),
                    support.emailMasked(tenantId, workspace.getMemberId())), now, null);
            });
        } catch (DuplicateKeyException duplicate) {
            String message = duplicate.getMostSpecificCause().getMessage();
            if (message != null && message.contains("uk_tenant_invite_active")) {
                throw new FailException(ClientTeamErrorCode.CLIENT_INVITATION_DUPLICATE);
            }
            throw duplicate;
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
    }

    /** 新 PENDING 邀请对象：冻结邮箱 Token、成员类型与初始角色，到期为 7 天后。 */
    TenantInvitation newInvitation(long invitationId, long tenantId, InvitationEmail email, ClientTeamRole role,
        long roleId, long inviterMemberId, java.time.Instant now) {
        var invitation = new TenantInvitation();
        invitation.invitationId(invitationId);
        invitation.invitationNo(preparation.invitationNo());
        invitation.tenantId(tenantId);
        invitation.invitedEmailToken(email.token());
        invitation.invitedEmailMasked(email.masked());
        invitation.memberType(ClientTeamRules.memberType(role));
        invitation.initialRoleId(roleId);
        invitation.invitedByMemberId(inviterMemberId);
        invitation.expiresTime(now.plus(ClientTeamRules.INVITATION_VALIDITY));
        return invitation;
    }
}

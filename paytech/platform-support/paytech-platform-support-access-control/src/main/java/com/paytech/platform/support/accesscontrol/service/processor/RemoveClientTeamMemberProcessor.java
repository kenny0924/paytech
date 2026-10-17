package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamPermissions;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TeamMemberChange;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TeamMemberView;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamMemberRoleRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientTeamService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientTeamRequest;
import com.paytech.platform.support.api.enums.ClientTeamErrorCode;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/**
 * 移除成员（需要 CLIENT_TEAM_MANAGE）：成员与其全部当前授予置为 REVOKED
 * <li>锁 tenant-member:{tenantId}:{memberId}；锁前只读解析当前工作区。</li>
 * <li>不属于本工作区 404；所有者与自己 409 CLIENT_MEMBER_PROTECTED；已移除返回原结果、不重复审计。</li>
 * <li>被移除成员的下一次请求因成员非 ACTIVE 失败关闭，工作区列表不再显示该工作区；之后可重新邀请。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class RemoveClientTeamMemberProcessor extends AbstractLockProcessor<ClientTeamRequest, TeamMemberView> {
    private final ClientTeamSupport support;
    private final OperationsAuditService audit;
    private final ClientTeamService service;
    private final TeamMemberRepository members;
    private final TeamMemberRoleRepository grants;
    private final ClientWorkspaceAccessSupport access;
    private final ClientInvitationPreparation preparation;

    @Override
    protected LockCommand lockCommand(ClientTeamRequest request) {
        var workspace = access.readTransaction().execute(status -> access.require(request.getAccountId(),
            request.getSessionId(), ClientTeamPermissions.TEAM_MANAGE));
        return LockCommand.tryLock(ClientInvitationPreparation.memberLock(workspace.getTenantId(), request.getTargetId()),
            Math.toIntExact(preparation.lockWait().toMillis()), Math.toIntExact(preparation.lockLease().toMillis()));
    }

    @Override
    protected TeamMemberView processWithinLock(ClientTeamRequest request) {
        try {
            return access.writeTransaction().execute(status -> {
                audit.observeTransaction();
                var workspace = access.require(request.getAccountId(), request.getSessionId(),
                    ClientTeamPermissions.TEAM_MANAGE);
                var now = preparation.now();
                var member = members.lock(workspace.getTenantId(), request.getTargetId());
                if (member == null) {
                    throw new FailException(CommonErrorCode.NOT_FOUND);
                }
                if (member.memberType() == TenantMemberType.OWNER || member.memberId() == workspace.getMemberId()) {
                    throw new FailException(ClientTeamErrorCode.CLIENT_MEMBER_PROTECTED);
                }
                if (member.status() == OperationsMemberStatus.REVOKED) {
                    audit.suppressReplay();
                    return support.memberView(member, null);
                }
                if (member.status() != OperationsMemberStatus.ACTIVE) {
                    throw new FailException(CommonErrorCode.NOT_FOUND);
                }
                var current = grants.lockActive(workspace.getTenantId(), member.memberId());
                service.removeMember(new TeamMemberChange(member, current, null, null, now));
                audit.record(() -> Outcome.succeeded(Long.toString(workspace.getAccountId()), workspace.getTenantId(),
                    new Target("TENANT_MEMBER", Long.toString(member.memberId())), "MEMBER_REMOVED", now));
                return support.memberView(member, null);
            });
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
    }
}

package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMemberGrant;
import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientWorkspaceAccess;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamPermissions;
import com.paytech.platform.support.accesscontrol.domain.clientteam.ClientTeamRules;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TeamMemberChange;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TeamMemberView;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsMemberStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamMemberRoleRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientTeamService;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientTeamRequest;
import com.paytech.platform.support.api.enums.ClientTeamErrorCode;
import com.paytech.platform.support.api.enums.ClientTeamRole;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Target;
import com.paytech.platform.support.auditsecurity.domain.audit.OperationsAuditFact.Outcome;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

import lombok.AllArgsConstructor;

/**
 * 调整成员角色（需要 CLIENT_TEAM_MANAGE）：撤销全部当前授予并授予新角色，成员类型随角色变化
 * <li>锁 tenant-member:{tenantId}:{memberId}，与移除、接受恢复同一互斥域；锁前只读解析当前工作区。</li>
 * <li>不属于本工作区或非 ACTIVE 404；所有者与自己 409 CLIENT_MEMBER_PROTECTED；角色未变返回原结果、不重复审计。</li>
 * <li>权限每次请求实时读取，下一次请求即按新角色判断。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ChangeClientTeamMemberRoleProcessor extends AbstractLockProcessor<ClientTeamRequest, TeamMemberView> {
    private final ClientTeamSupport support;
    private final OperationsAuditService audit;
    private final ClientTeamService service;
    private final TeamMemberRepository members;
    private final TeamMemberRoleRepository grants;
    private final ClientWorkspaceAccessSupport access;
    private final ClientInvitationPreparation preparation;

    /** 角色只依赖输入，查询前校验。 */
    @Override
    protected void validate(ClientTeamRequest request) {
        ClientTeamRules.assignableRole(request.getRoleCode());
    }

    @Override
    protected LockCommand lockCommand(ClientTeamRequest request) {
        var workspace = access.readTransaction().execute(status -> access.require(request.getAccountId(),
            request.getSessionId(), ClientTeamPermissions.TEAM_MANAGE));
        return LockCommand.tryLock(ClientInvitationPreparation.memberLock(workspace.getTenantId(), request.getTargetId()),
            Math.toIntExact(preparation.lockWait().toMillis()), Math.toIntExact(preparation.lockLease().toMillis()));
    }

    @Override
    protected TeamMemberView processWithinLock(ClientTeamRequest request) {
        var role = ClientTeamRules.assignableRole(request.getRoleCode());
        try {
            return access.writeTransaction().execute(status -> {
                audit.observeTransaction();
                var workspace = access.require(request.getAccountId(), request.getSessionId(),
                    ClientTeamPermissions.TEAM_MANAGE);
                var now = preparation.now();
                var member = requireChangeable(workspace, members.lock(workspace.getTenantId(), request.getTargetId()));
                var current = grants.lockActive(workspace.getTenantId(), member.memberId());
                var memberType = ClientTeamRules.memberType(role);
                if (unchanged(member, current, role, memberType)) {
                    audit.suppressReplay();
                    return support.memberView(member, role.getCode());
                }
                var targetRole = support.requireRole(workspace.getTenantId(), role);
                var grant = new TeamMemberGrant(preparation.nextId(), member.memberId(), workspace.getTenantId(),
                    targetRole.roleId(), role.getCode(), now, workspace.getMemberId(), null, 0);
                service.changeMemberRole(new TeamMemberChange(member, current, grant, memberType, now));
                audit.record(() -> Outcome.succeeded(Long.toString(workspace.getAccountId()), workspace.getTenantId(),
                    new Target("TENANT_MEMBER", Long.toString(member.memberId())), "MEMBER_ROLE_CHANGED", now));
                return support.memberView(member, role.getCode());
            });
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("发生并发修改，请刷新后重试", failure);
        }
    }

    /** 成员必须是本工作区 ACTIVE 成员，且不是所有者或操作人自己。 */
    TeamMember requireChangeable(ClientWorkspaceAccess workspace, TeamMember member) {
        if (member == null || member.status() != OperationsMemberStatus.ACTIVE) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        if (member.memberType() == TenantMemberType.OWNER || member.memberId() == workspace.getMemberId()) {
            throw new FailException(ClientTeamErrorCode.CLIENT_MEMBER_PROTECTED);
        }
        return member;
    }

    /** 已只持有目标角色且成员类型一致时视为未变。 */
    boolean unchanged(TeamMember member, List<TeamMemberGrant> current, ClientTeamRole role, TenantMemberType type) {
        return current.size() == 1 && role.getCode().equals(current.getFirst().roleCode()) && member.memberType() == type;
    }
}

package com.paytech.platform.support.accesscontrol.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamRole;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantInvitation;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TeamMemberView;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationView;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantRoleStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamMemberRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.TeamRoleRepository;
import com.paytech.platform.support.api.enums.ClientTeamRole;

import lombok.AllArgsConstructor;

/** 团队用例共用的本库只读查询与结果组装；不写库，调用方负责事务。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ClientTeamSupport {
    private final TeamRoleRepository roles;
    private final TeamMemberRepository members;

    /**
     * 本工作区当前有效的系统角色
     * <li>角色在首次 ACTIVE 投影或存量回填时创建；缺失或停用属于系统状态异常，返回 500。</li>
     */
    TeamRole requireRole(long tenantId, ClientTeamRole role) {
        TeamRole found = roles.systemRole(tenantId, role.getCode());
        if (found == null || found.status() != TenantRoleStatus.ACTIVE) {
            throw new ErrorException("工作区系统角色未就绪：" + role.getCode() + "，租户：" + tenantId);
        }
        return found;
    }

    /** 成员的账号脱敏邮箱；成员不存在为空。 */
    String emailMasked(long tenantId, long memberId) {
        TeamMember member = members.member(tenantId, memberId);
        return member == null ? null : member.emailMasked();
    }

    /** 由邀请对象、初始角色代码与发起人脱敏邮箱组装结果。 */
    TenantInvitationView invitationView(TenantInvitation invitation, String roleCode, String invitedByEmailMasked) {
        return new TenantInvitationView(invitation.invitationId(), invitation.invitedEmailMasked(), roleCode,
            invitation.status().getCode(), invitation.expiresTime(), invitedByEmailMasked, invitation.createTime());
    }

    /** 由成员对象与当前角色代码组装结果；移除后的成员角色为空。 */
    TeamMemberView memberView(TeamMember member, String roleCode) {
        return new TeamMemberView(member.memberId(), member.accountId(), member.emailMasked(),
            member.memberType().getCode(), roleCode, member.status().getCode(), member.createTime());
    }
}

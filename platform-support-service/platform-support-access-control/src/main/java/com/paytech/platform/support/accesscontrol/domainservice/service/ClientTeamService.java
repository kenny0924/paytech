package com.paytech.platform.support.accesscontrol.domainservice.service;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.InvitationChallenge;
import com.paytech.platform.support.accesscontrol.domain.clientteam.AcceptTenantInvitationContext;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TeamMemberChange;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationChange;

/**
 * CLIENT 工作区团队与邀请的本地保存
 * <li>只操作本库成员、授予、邀请与挑战表；调用方在自身写事务内调用，并已完成会话、权限与归属重证。</li>
 */
public interface ClientTeamService {
    /** 释放同邮箱已到期旧邀请的活动位，写入新邀请及其挑战。 */
    void createInvitation(TenantInvitationChange change);

    /** 替代原活动挑战、重新计算到期并写入新挑战。 */
    void resendInvitation(TenantInvitationChange change);

    /** 撤销邀请及其活动挑战。 */
    void revokeInvitation(TenantInvitationChange change);

    /** 撤销成员全部当前授予、授予新角色并同步成员类型。 */
    void changeMemberRole(TeamMemberChange change);

    /** 撤销成员全部当前授予并移除成员。 */
    void removeMember(TeamMemberChange change);

    /** 创建或恢复成员、授予初始角色、消费挑战并完成邀请。 */
    void acceptInvitation(AcceptTenantInvitationContext context);

    /** 记一次链接秘密校验失败。 */
    void failProof(InvitationChallenge challenge, Instant now);
}

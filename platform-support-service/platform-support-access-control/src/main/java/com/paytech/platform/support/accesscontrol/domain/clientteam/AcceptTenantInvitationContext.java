package com.paytech.platform.support.accesscontrol.domain.clientteam;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.InvitationChallenge;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMemberGrant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantInvitation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 接受邀请的本地保存上下文：创建或恢复成员、授予初始角色、消费挑战、完成邀请在一个事务内完成
 * <li>newMember：本账号在该工作区没有成员记录，插入 member。</li>
 * <li>restore：成员已 REVOKED，恢复为 ACTIVE 并改为邀请的成员类型。</li>
 * <li>两者都否：已是 ACTIVE 成员，只消费挑战并完成邀请，不改授予（newGrant 为空）。</li>
 */
@Getter
@AllArgsConstructor
public class AcceptTenantInvitationContext {
    /** 已锁定的 PENDING 邀请。 */
    private final TenantInvitation invitation;

    /** 已锁定的活动挑战。 */
    private final InvitationChallenge challenge;

    /** 新建或已锁定的成员。 */
    private final TeamMember member;

    /** 是否新建成员。 */
    private final boolean newMember;

    /** 是否恢复已移除的成员。 */
    private final boolean restore;

    /** 恢复时需撤销的残留授予。 */
    private final List<TeamMemberGrant> revokedGrants;

    /** 初始角色授予；已是 ACTIVE 成员时为空。 */
    private final TeamMemberGrant newGrant;

    /** 本次统一 UTC 微秒时间。 */
    private final Instant now;

    /** 是否本次加入（新建或恢复）。 */
    public boolean joins() {
        return newMember || restore;
    }
}

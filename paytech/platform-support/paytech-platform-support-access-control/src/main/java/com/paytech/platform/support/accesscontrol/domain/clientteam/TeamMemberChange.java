package com.paytech.platform.support.accesscontrol.domain.clientteam;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMemberGrant;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 调整成员角色或移除成员的本地保存上下文；成员与授予已在本事务内锁定
 * <li>调整角色：撤销全部当前授予，写入 newGrant，成员类型改为 memberType。</li>
 * <li>移除：撤销全部当前授予，成员置为 REVOKED；newGrant 与 memberType 为空。</li>
 */
@Getter
@AllArgsConstructor
public class TeamMemberChange {
    /** 已锁定的成员。 */
    private final TeamMember member;

    /** 需撤销的当前授予。 */
    private final List<TeamMemberGrant> revokedGrants;

    /** 新授予；移除为空。 */
    private final TeamMemberGrant newGrant;

    /** 新成员类型；移除为空。 */
    private final TenantMemberType memberType;

    /** 本次统一 UTC 微秒时间。 */
    private final Instant now;
}

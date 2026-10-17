package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMember;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TeamMemberView;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;

/** CLIENT 工作区成员（t_tenant_member）的团队管理持久化合同；条件更新成功后同步传入对象。 */
public interface TeamMemberRepository {
    /** 本工作区 ACTIVE 成员一页。 */
    List<TeamMemberView> members(long tenantId, Instant now, int offset, int size);

    /** 本工作区 ACTIVE 成员总数。 */
    long countMembers(long tenantId);

    /** 读取本工作区的成员；不存在为空。 */
    TeamMember member(long tenantId, long memberId);

    /** 行锁读取本工作区的成员；不存在为空。 */
    TeamMember lock(long tenantId, long memberId);

    /** 读取账号在本工作区的成员；不存在为空。 */
    TeamMember memberByAccount(long tenantId, long accountId);

    /** 行锁读取账号在本工作区的成员；不存在为空。 */
    TeamMember lockByAccount(long tenantId, long accountId);

    /** 受邀邮箱是否已是本工作区 ACTIVE 成员。 */
    boolean activeMemberExists(long tenantId, String emailToken);

    /** 写入新 ACTIVE 成员；重复成员由唯一索引抛出。 */
    void insert(TeamMember member, Instant now);

    /** ACTIVE 成员改类型。 */
    void changeType(TeamMember member, TenantMemberType memberType, Instant now);

    /** ACTIVE 成员置为 REVOKED。 */
    void revoke(TeamMember member, Instant now);

    /** REVOKED 成员恢复为 ACTIVE。 */
    void restore(TeamMember member, TenantMemberType memberType, long invitedBy, Instant now);
}

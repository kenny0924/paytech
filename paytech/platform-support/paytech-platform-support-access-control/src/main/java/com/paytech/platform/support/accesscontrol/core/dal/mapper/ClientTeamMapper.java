package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.InvitationChallenge;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamMemberGrant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamRole;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TeamTenant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantInvitation;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TeamMemberView;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationView;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantMemberType;

/** CLIENT 工作区团队与邀请的 SQL；SQL 位于 ClientTeamMapper.xml。 */
public interface ClientTeamMapper {

    /** 本工作区 ACTIVE 成员一页：所有者在前，其余按加入时间。 */
    List<TeamMemberView> members(@Param("tenantId") long tenantId, @Param("now") Instant now,
        @Param("offset") int offset, @Param("size") int size);

    /** 本工作区 ACTIVE 成员总数。 */
    long countMembers(@Param("tenantId") long tenantId);

    /** 读取本工作区的成员（含账号脱敏邮箱）。 */
    TeamMember member(@Param("tenantId") long tenantId, @Param("memberId") long memberId);

    /** 行锁读取本工作区的成员。 */
    TeamMember lockMember(@Param("tenantId") long tenantId, @Param("memberId") long memberId);

    /** 读取账号在本工作区的成员。 */
    TeamMember memberByAccount(@Param("tenantId") long tenantId, @Param("accountId") long accountId);

    /** 行锁读取账号在本工作区的成员。 */
    TeamMember lockMemberByAccount(@Param("tenantId") long tenantId, @Param("accountId") long accountId);

    /** 受邀邮箱是否已是本工作区 ACTIVE 成员。 */
    int countActiveMembersByEmail(@Param("tenantId") long tenantId, @Param("emailToken") String emailToken);

    /** 写入新成员。 */
    int insertMember(@Param("member") TeamMember member, @Param("now") Instant now);

    /** ACTIVE 成员改类型。 */
    int changeMemberType(@Param("memberId") long memberId, @Param("version") int version,
        @Param("memberType") TenantMemberType memberType, @Param("now") Instant now);

    /** ACTIVE 成员置为 REVOKED。 */
    int revokeMember(@Param("memberId") long memberId, @Param("version") int version, @Param("now") Instant now);

    /** REVOKED 成员恢复为 ACTIVE，并记录本次邀请人。 */
    int restoreMember(@Param("memberId") long memberId, @Param("version") int version,
        @Param("memberType") TenantMemberType memberType, @Param("invitedBy") long invitedBy, @Param("now") Instant now);

    /** 行锁读取成员当前全部 ACTIVE 授予（含角色代码）。 */
    List<TeamMemberGrant> lockActiveGrants(@Param("tenantId") long tenantId, @Param("memberId") long memberId);

    /** 撤销一项授予。 */
    int revokeGrant(@Param("grantId") long grantId, @Param("version") int version, @Param("now") Instant now);

    /** 写入一项授予。 */
    int insertGrant(@Param("grant") TeamMemberGrant grant, @Param("now") Instant now);

    /** 本工作区的系统角色。 */
    TeamRole systemRole(@Param("tenantId") long tenantId, @Param("roleCode") String roleCode);

    /** 本工作区的角色。 */
    TeamRole role(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    /** CLIENT 租户状态。 */
    TeamTenant tenant(@Param("tenantId") long tenantId);

    /** 本工作区邀请一页；status 为显示状态代码：PENDING 只含未到期，EXPIRED 含已到期的 PENDING；按创建时间倒序。 */
    List<TenantInvitationView> invitations(@Param("tenantId") long tenantId,
        @Param("status") String status, @Param("now") Instant now, @Param("offset") int offset,
        @Param("size") int size);

    /** 与 invitations 相同条件的总数。 */
    long countInvitations(@Param("tenantId") long tenantId, @Param("status") String status,
        @Param("now") Instant now);

    /** 本工作区未到期的 PENDING 邀请数。 */
    int countPendingInvitations(@Param("tenantId") long tenantId, @Param("now") Instant now);

    /** 读取本工作区的邀请。 */
    TenantInvitation invitation(@Param("tenantId") long tenantId, @Param("invitationId") long invitationId);

    /** 按主键读取邀请（链接证明路径，租户由邀请确定）。 */
    TenantInvitation invitationById(@Param("invitationId") long invitationId);

    /** 行锁读取邀请。 */
    TenantInvitation lockInvitation(@Param("invitationId") long invitationId);

    /** 行锁读取同租户同邮箱占用活动位的邀请。 */
    TenantInvitation lockActiveInvitation(@Param("tenantId") long tenantId, @Param("emailToken") String emailToken);

    /** 写入新 PENDING 邀请。 */
    int insertInvitation(@Param("invitation") TenantInvitation invitation, @Param("now") Instant now);

    /** 已到期的 PENDING 邀请置为 EXPIRED 并释放活动位。 */
    int expireInvitation(@Param("invitationId") long invitationId, @Param("version") int version,
        @Param("now") Instant now);

    /** PENDING 邀请重新计算到期时间。 */
    int renewInvitation(@Param("invitationId") long invitationId, @Param("version") int version,
        @Param("expiresTime") Instant expiresTime, @Param("now") Instant now);

    /** PENDING 邀请撤销并释放活动位。 */
    int revokeInvitation(@Param("invitationId") long invitationId, @Param("version") int version,
        @Param("now") Instant now);

    /** 未到期的 PENDING 邀请完成接受并释放活动位。 */
    int acceptInvitation(@Param("invitationId") long invitationId, @Param("version") int version,
        @Param("accountId") long accountId, @Param("memberId") long memberId, @Param("now") Instant now);

    /** 按公开编号读取邀请接受挑战。 */
    InvitationChallenge challenge(@Param("challengeNo") String challengeNo);

    /** 行锁读取邀请接受挑战。 */
    InvitationChallenge lockChallenge(@Param("challengeId") long challengeId);

    /** 行锁读取邀请当前的活动挑战。 */
    InvitationChallenge lockActiveChallenge(@Param("invitationId") long invitationId);

    /** 写入新挑战，仅持久化 HMAC。 */
    int insertChallenge(@Param("challenge") InvitationChallenge challenge);

    /** 替代活动挑战。 */
    int supersedeChallenge(@Param("challengeId") long challengeId, @Param("version") int version,
        @Param("now") Instant now);

    /** 撤销活动挑战（邀请被撤销）。 */
    int revokeChallenge(@Param("challengeId") long challengeId, @Param("version") int version,
        @Param("now") Instant now);

    /** 单次消费未到期、未用尽的活动挑战。 */
    int consumeChallenge(@Param("challengeId") long challengeId, @Param("version") int version,
        @Param("now") Instant now);

    /** 记一次失败尝试，到期或用尽时关闭挑战。 */
    int failChallenge(@Param("challengeId") long challengeId, @Param("version") int version,
        @Param("now") Instant now);
}

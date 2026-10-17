package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantInvitation;
import com.paytech.platform.support.accesscontrol.domain.clientteam.TenantInvitationView;

/** 租户成员邀请（t_tenant_invitation）单表持久化合同；条件更新成功后同步传入对象。 */
public interface TenantInvitationRepository {
    /** 本工作区邀请一页，status 为显示状态代码。 */
    List<TenantInvitationView> invitations(long tenantId, String status, Instant now, int offset, int size);

    /** 与列表相同条件的总数。 */
    long countInvitations(long tenantId, String status, Instant now);

    /** 本工作区未到期的 PENDING 邀请数。 */
    int countPending(long tenantId, Instant now);

    /** 读取本工作区的邀请；不存在为空。 */
    TenantInvitation invitation(long tenantId, long invitationId);

    /** 按主键读取邀请；不存在为空。 */
    TenantInvitation invitation(long invitationId);

    /** 行锁读取邀请；不存在为空。 */
    TenantInvitation lock(long invitationId);

    /** 行锁读取同租户同邮箱占用活动位的邀请；不存在为空。 */
    TenantInvitation lockActive(long tenantId, String emailToken);

    /** 写入新 PENDING 邀请。 */
    void insert(TenantInvitation invitation, Instant now);

    /** 已到期的 PENDING 邀请置为 EXPIRED。 */
    void expire(TenantInvitation invitation, Instant now);

    /** 重新计算到期时间。 */
    void renew(TenantInvitation invitation, Instant expiresTime, Instant now);

    /** 撤销 PENDING 邀请。 */
    void revoke(TenantInvitation invitation, Instant now);

    /** 完成接受。 */
    void accept(TenantInvitation invitation, long accountId, long memberId, Instant now);
}

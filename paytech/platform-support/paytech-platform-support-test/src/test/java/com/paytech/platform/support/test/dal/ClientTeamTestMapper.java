package com.paytech.platform.support.test.dal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 团队与邀请测试的只读检查、到期调整，以及邀请邮件模板的插入与按 ID 清理。 */
public interface ClientTeamTestMapper {

    /** 邀请邮件即时发送模板（编码与 application-mock.yml 的 notification.transient-template-codes 一致）。 */
    @Insert("INSERT INTO t_notification_template VALUES(890021,'CLIENT_TENANT_INVITATION','EMAIL','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    int insertInvitationTemplate();

    /** 邀请模板的 zh-CN 内容。 */
    @Insert("INSERT INTO t_notification_template_content VALUES(890022,890021,'zh-CN',#{subject},#{body},1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    int insertInvitationContent(@Param("subject") String subject, @Param("body") String body);

    /** 邀请模板只路由到 SES 测试账号（890007，由 NotificationFixture 插入）。 */
    @Insert("INSERT INTO t_notification_route VALUES(890023,890021,890007,NULL,'ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    int insertInvitationRoute();

    /** 清理邀请模板路由。 */
    @Delete("DELETE FROM t_notification_route WHERE route_id=890023 OR notification_template_id=890021")
    int deleteInvitationRoute();

    /** 清理邀请模板内容。 */
    @Delete("DELETE FROM t_notification_template_content WHERE content_id=890022 OR template_id=890021")
    int deleteInvitationContent();

    /** 清理邀请模板。 */
    @Delete("DELETE FROM t_notification_template WHERE template_id=890021")
    int deleteInvitationTemplate();

    /** 本例租户的邀请行，按创建先后。 */
    @Select("SELECT * FROM t_tenant_invitation WHERE tenant_id=#{tenantId} ORDER BY create_time, invitation_id")
    List<Map<String, Object>> invitations(long tenantId);

    /** 邀请的全部挑战行，按创建先后。 */
    @Select("SELECT * FROM t_authentication_challenge WHERE tenant_invitation_id=#{invitationId} ORDER BY create_time, challenge_id")
    List<Map<String, Object>> challenges(long invitationId);

    /** 把邀请及其活动挑战的到期时间改到给定时刻，模拟已过 7 天。 */
    @Update("UPDATE t_tenant_invitation SET expires_time=#{expires} WHERE invitation_id=#{id}")
    int expireInvitation(@Param("id") long id, @Param("expires") Instant expires);

    /** 同步把邀请的挑战到期时间改到给定时刻。 */
    @Update("UPDATE t_authentication_challenge SET expires_time=#{expires} WHERE tenant_invitation_id=#{id}")
    int expireChallenges(@Param("id") long id, @Param("expires") Instant expires);

    /** 本例租户的全部成员行。 */
    @Select("SELECT * FROM t_tenant_member WHERE tenant_id=#{tenantId} ORDER BY create_time, member_id")
    List<Map<String, Object>> members(long tenantId);

    /** 成员当前 ACTIVE 授予的角色代码。 */
    @Select("""
        SELECT r.role_code FROM t_tenant_member_role g JOIN t_role r ON r.role_id=g.role_id
        WHERE g.tenant_member_id=#{memberId} AND g.status='ACTIVE' ORDER BY r.role_code
        """)
    List<String> activeRoles(long memberId);

    /** 租户的系统角色代码及其 ACTIVE 权限码，按角色、权限排序。 */
    @Select("""
        SELECT r.role_code, p.permission_code FROM t_role r JOIN t_role_permission rp ON rp.role_id=r.role_id
        JOIN t_permission p ON p.permission_id=rp.permission_id
        WHERE r.tenant_id=#{tenantId} AND r.system_role=1 AND rp.status='ACTIVE' ORDER BY r.role_code, p.permission_code
        """)
    List<Map<String, Object>> systemRolePermissions(long tenantId);
}

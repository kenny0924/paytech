package com.paytech.platform.support.test.dal;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 按租户权限解析通知收件人的场景调整：成员、角色授予、角色权限与账号邮箱的状态变化；行由 PlatformDataCleaner 整表回收。 */
public interface TenantRecipientTestMapper {

    /** 租户的所有者引用。 */
    @Select("SELECT owner_ref_id FROM t_access_tenant WHERE tenant_id=#{tenantId}")
    long ownerRefId(long tenantId);

    /** 停用成员。 */
    @Update("UPDATE t_tenant_member SET status='SUSPENDED' WHERE member_id=#{memberId}")
    int suspendMember(long memberId);

    /** 把成员全部角色授予的失效时间改到给定时刻（状态仍为 ACTIVE），模拟授予已过期。 */
    @Update("UPDATE t_tenant_member_role SET effective_to=#{to} WHERE tenant_member_id=#{memberId}")
    int expireMemberRoles(@Param("memberId") long memberId, @Param("to") Instant to);

    /** 撤销角色上的某个权限。 */
    @Update("""
        UPDATE t_role_permission rp JOIN t_permission p ON p.permission_id=rp.permission_id
        SET rp.status='REVOKED' WHERE rp.role_id=#{roleId} AND p.permission_code=#{code}
        """)
    int revokeRolePermission(@Param("roleId") long roleId, @Param("code") String code);

    /** 清除账号的邮箱验证时间，模拟邮箱未验证。 */
    @Update("UPDATE t_user_account SET email_verified_time=NULL WHERE account_id=#{accountId}")
    int unverifyEmail(long accountId);

    /** 当前用例的投递邮箱 Token，按投递 ID 升序；只在测试断言中读取合成 Token。 */
    @Select("""
        SELECT d.address_vault_token FROM t_notification_delivery d JOIN t_notification_request r ON r.request_id=d.request_id
        WHERE r.source_system LIKE 'notification-test%' ORDER BY d.delivery_id
        """)
    java.util.List<String> deliveryTokens();
}

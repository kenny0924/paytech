package com.paytech.platform.support.test.dal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Operations 人工认证与 IAM 的测试数据准备和只读检查；业务命令必须经过真实 HTTP。 */
public interface OperationsTestMapper {

    /** 插入访问租户。 */
    @Insert("""
        INSERT INTO t_access_tenant(tenant_id,tenant_type,owner_ref_id,tenant_name,owner_status,security_status,
            version,create_time,update_time)
        VALUES(#{id},#{type},#{ownerRefId},#{name},#{ownerStatus},#{securityStatus},1,#{now},#{now})
        """)
    int insertTenant(@Param("id") long id, @Param("type") String type, @Param("ownerRefId") long ownerRefId,
        @Param("name") String name, @Param("ownerStatus") String ownerStatus,
        @Param("securityStatus") String securityStatus, @Param("now") Instant now);

    /** 删除本例登记的租户。 */
    @Delete("DELETE FROM t_access_tenant WHERE tenant_id=#{id}")
    int deleteTenant(long id);

    /** 修改租户状态，模拟另一业务操作已提交的安全或所有者状态变化。 */
    @Update("UPDATE t_access_tenant SET owner_status=#{ownerStatus},security_status=#{securityStatus},version=version+1 WHERE tenant_id=#{id}")
    int updateTenantStatus(@Param("id") long id, @Param("ownerStatus") String ownerStatus,
        @Param("securityStatus") String securityStatus);

    /** 插入 Realm 的签名公钥。 */
    @Insert("""
        INSERT INTO t_jwt_signing_key(signing_key_id,realm,kid,algorithm,kms_key_ref,public_jwk_schema_version,
            public_jwk_canonical_json,status,active_slot,not_before,signing_end_time,verification_end_time,version,
            create_time,update_time)
        VALUES(#{id},#{realm},#{kid},'RS256',#{keyRef},#{schemaVersion},#{jwk},#{status},#{activeSlot},#{notBefore},
            #{signingEnd},#{verificationEnd},1,#{now},#{now})
        """)
    int insertSigningKey(@Param("id") long id, @Param("realm") String realm, @Param("kid") String kid,
        @Param("keyRef") String keyRef, @Param("schemaVersion") int schemaVersion, @Param("jwk") String jwk,
        @Param("status") String status,
        @Param("activeSlot") Integer activeSlot, @Param("notBefore") Instant notBefore,
        @Param("signingEnd") Instant signingEnd, @Param("verificationEnd") Instant verificationEnd,
        @Param("now") Instant now);

    /** 删除 Realm 的全部签名密钥，供密钥轮换场景重建候选集合。 */
    @Delete("DELETE FROM t_jwt_signing_key WHERE realm=#{realm}")
    int deleteSigningKeys(String realm);

    /** 插入权限目录条目。 */
    @Insert("""
        INSERT INTO t_permission(permission_id,permission_code,resource_type,action_code,risk_level,status,version,
            create_time,update_time)
        VALUES(#{id},#{code},#{resourceType},#{action},#{risk},#{status},1,#{now},#{now})
        """)
    int insertPermission(@Param("id") long id, @Param("code") String code, @Param("resourceType") String resourceType,
        @Param("action") String action, @Param("risk") String risk, @Param("status") String status,
        @Param("now") Instant now);

    /** 插入平台或租户角色。 */
    @Insert("""
        INSERT INTO t_role(role_id,tenant_scope,tenant_id,scope_key,role_code,role_name,status,system_role,version,
            create_time,update_time)
        VALUES(#{id},#{tenantScope},#{tenantId},#{scopeKey},#{code},#{name},#{status},#{systemRole},1,#{now},#{now})
        """)
    int insertRole(@Param("id") long id, @Param("tenantScope") String tenantScope, @Param("tenantId") Long tenantId,
        @Param("scopeKey") String scopeKey, @Param("code") String code, @Param("name") String name,
        @Param("status") String status, @Param("systemRole") boolean systemRole, @Param("now") Instant now);

    /** 插入角色授权。 */
    @Insert("""
        INSERT INTO t_role_permission(role_permission_id,role_id,permission_id,status,version,create_time,update_time)
        VALUES(#{id},#{roleId},#{permissionId},#{status},1,#{now},#{now})
        """)
    int insertRolePermission(@Param("id") long id, @Param("roleId") long roleId,
        @Param("permissionId") long permissionId, @Param("status") String status, @Param("now") Instant now);

    /** 插入 Operations 或 Customer 账号。 */
    @Insert("""
        INSERT INTO t_user_account(account_id,realm,account_type,login_name,email_vault_token,email_masked,
            email_verified_time,status,consecutive_password_failures,locked_until,credential_version,auth_epoch,
            version,create_time,update_time)
        VALUES(#{id},#{realm},#{type},#{loginName},#{emailToken},#{emailMasked},#{emailVerified},#{status},0,
            #{lockedUntil},1,1,1,#{now},#{now})
        """)
    int insertAccount(@Param("id") long id, @Param("realm") String realm, @Param("type") String type,
        @Param("loginName") String loginName, @Param("emailToken") String emailToken,
        @Param("emailMasked") String emailMasked, @Param("emailVerified") Instant emailVerified,
        @Param("status") String status, @Param("lockedUntil") Instant lockedUntil, @Param("now") Instant now);

    /** 插入 Argon2id 密码凭据；PHC 在测试侧计算。 */
    @Insert("""
        INSERT INTO t_password_credential(password_credential_id,account_id,password_verifier_phc,algorithm_code,
            algorithm_version,memory_kib,iterations,parallelism,credential_version,force_change_flag,status,
            password_changed_time,version,create_time,update_time)
        VALUES(#{id},#{accountId},#{phc},'ARGON2ID',19,19456,2,1,1,#{forceChange},#{status},#{now},1,#{now},#{now})
        """)
    int insertPassword(@Param("id") long id, @Param("accountId") long accountId, @Param("phc") String phc,
        @Param("forceChange") boolean forceChange, @Param("status") String status, @Param("now") Instant now);

    /** 插入租户成员。 */
    @Insert("""
        INSERT INTO t_tenant_member(member_id,tenant_id,account_id,member_type,status,invited_by,version,
            create_time,update_time)
        VALUES(#{id},#{tenantId},#{accountId},#{type},#{status},NULL,1,#{now},#{now})
        """)
    int insertMember(@Param("id") long id, @Param("tenantId") long tenantId, @Param("accountId") long accountId,
        @Param("type") String type, @Param("status") String status, @Param("now") Instant now);

    /** 插入成员角色授权及有效期。 */
    @Insert("""
        INSERT INTO t_tenant_member_role(tenant_member_role_id,tenant_member_id,tenant_id,role_id,role_scope_key,
            effective_from,effective_to,granted_by,status,version,create_time,update_time)
        VALUES(#{id},#{memberId},#{tenantId},#{roleId},#{scopeKey},#{from},#{to},#{grantedBy},#{status},1,
            #{now},#{now})
        """)
    int insertMemberRole(@Param("id") long id, @Param("memberId") long memberId, @Param("tenantId") long tenantId,
        @Param("roleId") long roleId, @Param("scopeKey") String scopeKey, @Param("from") Instant from,
        @Param("to") Instant to, @Param("grantedBy") long grantedBy, @Param("status") String status,
        @Param("now") Instant now);

    /** 插入认证会话。 */
    @Insert("""
        INSERT INTO t_auth_session(session_id,account_id,realm,channel,context_type,tenant_id,tenant_member_id,
            authentication_method,account_auth_epoch,credential_version_snapshot,issued_time,
            last_reauthenticated_time,last_activity_time,idle_expire_time,absolute_expire_time,current_generation_no,
            status,version,create_time,update_time)
        VALUES(#{id},#{accountId},#{realm},#{channel},#{contextType},#{tenantId},#{memberId},'PASSWORD',1,1,#{now},
            #{now},#{now},#{idleExpire},#{absoluteExpire},1,#{status},1,#{now},#{now})
        """)
    int insertSession(@Param("id") long id, @Param("accountId") long accountId, @Param("realm") String realm,
        @Param("channel") String channel, @Param("contextType") String contextType, @Param("tenantId") Long tenantId,
        @Param("memberId") Long memberId, @Param("status") String status, @Param("idleExpire") Instant idleExpire,
        @Param("absoluteExpire") Instant absoluteExpire, @Param("now") Instant now);

    /** 插入当前刷新代；秘密只以 HMAC 保存。 */
    @Insert("""
        INSERT INTO t_refresh_token_generation(refresh_generation_id,session_id,generation_no,token_lookup_id,
            token_secret_hmac,hmac_key_version,issued_time,expires_time,status,active_slot,version,create_time,
            update_time)
        VALUES(#{id},#{sessionId},1,#{lookup},#{hmac},1,#{now},#{expires},'ISSUED',1,1,#{now},#{now})
        """)
    int insertRefresh(@Param("id") long id, @Param("sessionId") long sessionId, @Param("lookup") String lookup,
        @Param("hmac") String hmac, @Param("expires") Instant expires, @Param("now") Instant now);

    /** 修改账号状态、锁定与版本，模拟另一操作已提交的安全变化。 */
    @Update("""
        UPDATE t_user_account SET status=#{status},locked_until=#{lockedUntil},auth_epoch=#{authEpoch},
            credential_version=#{credentialVersion},version=version+1 WHERE account_id=#{id}
        """)
    int updateAccountSecurity(@Param("id") long id, @Param("status") String status,
        @Param("lockedUntil") Instant lockedUntil, @Param("authEpoch") int authEpoch,
        @Param("credentialVersion") int credentialVersion);

    /** 修改会话状态或过期时间。 */
    @Update("""
        UPDATE t_auth_session SET status=#{status},idle_expire_time=#{idleExpire},absolute_expire_time=#{absoluteExpire},
            version=version+1 WHERE session_id=#{id}
        """)
    int updateSession(@Param("id") long id, @Param("status") String status, @Param("idleExpire") Instant idleExpire,
        @Param("absoluteExpire") Instant absoluteExpire);

    /** 修改成员状态。 */
    @Update("UPDATE t_tenant_member SET status=#{status},version=version+1 WHERE member_id=#{id}")
    int updateMemberStatus(@Param("id") long id, @Param("status") String status);

    /** 修改密码凭据状态与强制改密标记。 */
    @Update("""
        UPDATE t_password_credential SET status=#{status},force_change_flag=#{forceChange},version=version+1
        WHERE account_id=#{accountId}
        """)
    int updatePassword(@Param("accountId") long accountId, @Param("status") String status,
        @Param("forceChange") boolean forceChange);

    /** 修改权限状态。 */
    @Update("UPDATE t_permission SET status=#{status},version=version+1 WHERE permission_id=#{id}")
    int updatePermissionStatus(@Param("id") long id, @Param("status") String status);

    /** 读取账号行，供数据库 Checker 断言。 */
    @Select("SELECT * FROM t_user_account WHERE account_id=#{id}")
    Map<String, Object> account(long id);

    /** 按 Realm 读取全部账号。 */
    @Select("SELECT * FROM t_user_account WHERE realm=#{realm} ORDER BY account_id")
    List<Map<String, Object>> accounts(String realm);

    /** 读取账号的密码凭据。 */
    @Select("SELECT * FROM t_password_credential WHERE account_id=#{accountId}")
    Map<String, Object> password(long accountId);

    /** 读取账号在内部租户的成员关系。 */
    @Select("SELECT * FROM t_tenant_member WHERE account_id=#{accountId} ORDER BY member_id")
    List<Map<String, Object>> members(long accountId);

    /** 读取成员的全部角色授权行。 */
    @Select("SELECT * FROM t_tenant_member_role WHERE tenant_member_id=#{memberId} ORDER BY role_id,effective_from")
    List<Map<String, Object>> memberRoles(long memberId);

    /** 读取会话行。 */
    @Select("SELECT * FROM t_auth_session WHERE session_id=#{id}")
    Map<String, Object> session(long id);

    /** 读取账号的全部会话。 */
    @Select("SELECT * FROM t_auth_session WHERE account_id=#{accountId} ORDER BY session_id")
    List<Map<String, Object>> sessions(long accountId);

    /** 读取会话的全部刷新代。 */
    @Select("SELECT * FROM t_refresh_token_generation WHERE session_id=#{sessionId} ORDER BY generation_no")
    List<Map<String, Object>> refreshes(long sessionId);

    /** 读取角色行。 */
    @Select("SELECT * FROM t_role WHERE role_id=#{id}")
    Map<String, Object> role(long id);

    /** 按编码读取平台角色。 */
    @Select("SELECT * FROM t_role WHERE scope_key='PLATFORM' AND role_code=#{code}")
    Map<String, Object> platformRole(String code);

    /** 读取角色的全部授权行。 */
    @Select("SELECT * FROM t_role_permission WHERE role_id=#{roleId} ORDER BY permission_id")
    List<Map<String, Object>> rolePermissions(long roleId);
}

package com.paytech.platform.support.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 工作区自助测试的只读检查与按租户清理；凭证两表不属于整表回收，只删除本例租户的行。 */
public interface ClientDeveloperTestMapper {

    /** 本例租户的凭证行。 */
    @Select("SELECT * FROM t_api_credential WHERE tenant_id=#{tenantId} ORDER BY create_time, credential_id")
    List<Map<String, Object>> credentials(long tenantId);

    /** 指定凭证的全部授权行。 */
    @Select("SELECT * FROM t_api_credential_scope WHERE credential_id=#{credentialId} ORDER BY scope_code")
    List<Map<String, Object>> scopes(long credentialId);

    /** 删除本例租户凭证的授权行。 */
    @Delete("""
        <script>DELETE FROM t_api_credential_scope WHERE credential_id IN
        (SELECT credential_id FROM (SELECT credential_id FROM t_api_credential WHERE tenant_id IN
        <foreach collection="tenantIds" item="id" open="(" separator="," close=")">#{id}</foreach>) c)</script>
        """)
    int deleteScopes(@Param("tenantIds") List<Long> tenantIds);

    /** 删除本例租户的凭证。 */
    @Delete("""
        <script>DELETE FROM t_api_credential WHERE tenant_id IN
        <foreach collection="tenantIds" item="id" open="(" separator="," close=")">#{id}</foreach></script>
        """)
    int deleteCredentials(@Param("tenantIds") List<Long> tenantIds);

    /** 直接把凭证到期时间改到过去，用于验证宽限截止后的认证失败。 */
    @org.apache.ibatis.annotations.Update("UPDATE t_api_credential SET expires_time=#{expires} WHERE credential_id=#{id}")
    int expire(@Param("id") long id, @Param("expires") java.time.Instant expires);

    /** 租户正式 OWNER 角色当前 ACTIVE 的权限码。 */
    @Select("""
        SELECT p.permission_code FROM t_role r JOIN t_role_permission rp ON rp.role_id=r.role_id
        JOIN t_permission p ON p.permission_id=rp.permission_id
        WHERE r.tenant_id=#{tenantId} AND r.role_code='TENANT_OWNER' AND rp.status='ACTIVE' ORDER BY p.permission_code
        """)
    List<String> ownerPermissions(long tenantId);
}

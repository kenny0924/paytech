package com.paytech.platform.support.test.dal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.paytech.platform.support.test.builder.CredentialFixture;
import com.paytech.platform.support.test.builder.CredentialScopeFixture;

/** 测试专用数据准备、只读检查和按本例身份清理；不替代生产查询 SQL。 */
public interface ApiCredentialTestMapper {

    /** 插入当前用例的租户事实。 */
    @Insert("""
        INSERT INTO t_access_tenant(tenant_id,tenant_type,owner_ref_id,tenant_name,owner_status,
            security_status,version,create_time,update_time)
        VALUES(#{f.tenantId},#{f.tenantType},#{f.ownerRefId},'PLT-I03 synthetic test tenant',
            #{f.ownerStatus},#{f.securityStatus},#{f.tenantVersion},#{now},#{now})
        """)
    int insertTenant(@Param("f") CredentialFixture fixture, @Param("now") Instant now);

    /** 只保存不可逆 verifier，原始 Credential 不作为数据库参数。 */
    @Insert("""
        INSERT INTO t_api_credential(credential_id,tenant_id,key_prefix,key_verifier_phc,
            expires_time,status,version,create_time,update_time)
        VALUES(#{f.credentialId},#{f.tenantId},#{f.keyPrefix},#{phc},#{f.expiresTime},
            #{f.credentialStatus},#{f.credentialVersion},#{now},#{now})
        """)
    int insertCredential(@Param("f") CredentialFixture fixture, @Param("phc") String phc,
        @Param("now") Instant now);

    /** 相同 Scope 的不同资源授权仍为不同数据库行。 */
    @Insert("""
        INSERT INTO t_api_credential_scope(scope_id,credential_id,scope_code,resource_pattern,
            action_code,effective_from,effective_to,status,version,create_time,update_time)
        VALUES(#{s.scopeId},#{credentialId},#{s.scopeCode},CONCAT('/test/',#{s.scopeId}),
            'READ',#{s.effectiveFrom},#{s.effectiveTo},#{s.status},#{s.version},#{now},#{now})
        """)
    int insertScope(@Param("credentialId") long credentialId, @Param("s") CredentialScopeFixture scope,
        @Param("now") Instant now);

    /** 用例之间不缓存资格；模拟另一次业务操作已提交的撤销。 */
    @Update("UPDATE t_api_credential SET status='REVOKED',version=version+1 WHERE credential_id=#{id}")
    int revoke(long id);

    /** 读取真实库名称，防止测试误连非本地业务库。 */
    @Select("SELECT DATABASE()")
    String databaseName();

    /** 快速检查表结构已经人工准备，不建表、不初始化业务库。 */
    @Select("SELECT credential_id,key_verifier_phc,expires_time FROM t_api_credential WHERE credential_id=-1")
    List<Map<String, Object>> checkSchema();

    /** 读取本例拥有的完整凭证行，仅作内存比较，不输出秘密字段。 */
    @Select("""
        <script>SELECT * FROM t_api_credential WHERE credential_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY credential_id</script>
        """)
    List<Map<String, Object>> credentials(@Param("ids") List<Long> ids);

    /** 读取本例拥有的完整租户行。 */
    @Select("""
        <script>SELECT * FROM t_access_tenant WHERE tenant_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY tenant_id</script>
        """)
    List<Map<String, Object>> tenants(@Param("ids") List<Long> ids);

    /** 读取本例凭证下的全部授权，不复用生产 effective 条件计算预期。 */
    @Select("""
        <script>SELECT * FROM t_api_credential_scope WHERE credential_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY scope_id</script>
        """)
    List<Map<String, Object>> scopes(@Param("ids") List<Long> ids);

    /** 认证只读请求不应产生持久化审计。 */
    @Select("SELECT COUNT(*) FROM t_audit_event WHERE request_id=#{requestId}")
    long auditCount(String requestId);

    /** 失败用例若意外产生审计，也只清理本例 Request ID 对应的属性。 */
    @Delete("DELETE FROM t_audit_event_attr WHERE audit_event_id IN (SELECT audit_event_id FROM t_audit_event WHERE request_id=#{requestId})")
    int deleteAuditAttributes(String requestId);

    /** 回收本例意外审计，不能触及其他请求。 */
    @Delete("DELETE FROM t_audit_event WHERE request_id=#{requestId}")
    int deleteAudit(String requestId);

    /** 先清理本例凭证的授权，绝不整表删除。 */
    @Delete("DELETE FROM t_api_credential_scope WHERE credential_id=#{id}")
    int deleteScopes(long id);

    /** 只删除成功插入且已登记归属的本例凭证。 */
    @Delete("DELETE FROM t_api_credential WHERE credential_id=#{id}")
    int deleteCredential(long id);

    /** 只删除成功插入且已登记归属的本例租户。 */
    @Delete("DELETE FROM t_access_tenant WHERE tenant_id=#{id}")
    int deleteTenant(long id);
}

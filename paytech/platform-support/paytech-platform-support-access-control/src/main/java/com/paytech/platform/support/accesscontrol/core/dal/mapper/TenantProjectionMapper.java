package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjection;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionMember;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;

/** PLT-I17 只访问 Platform 自己的 IAM 表。 */
public interface TenantProjectionMapper {
    /** 无副作用地读取准确 Tenant 当前权限。 */
    java.util.List<com.paytech.platform.support.accesscontrol.core.dal.dataobject.OnboardingTenantCandidate> fileReadCandidates(@Param("accountId") long accountId,@Param("tenantId") long tenantId,@Param("now") Instant now);
    /** 私网核验需要当前真实客户账号。 */
    TenantProjectionAccount fileAccount(@Param("id") long id);

    /** 只读命令回执使用原 Tenant 当前权限，不重新选择最新入驻身份。 */
    java.util.List<com.paytech.platform.support.accesscontrol.core.dal.dataobject.OnboardingTenantCandidate> lockFileReadCandidates(@Param("accountId") long accountId,@Param("tenantId") long tenantId,@Param("now") Instant now);

    /** 锁定当前账号全部有效 PENDING Client 入驻授权，调用者必须核对唯一候选与 CCT Client。 */
    java.util.List<com.paytech.platform.support.accesscontrol.core.dal.dataobject.OnboardingTenantCandidate> lockUploadCandidates(
        @Param("accountId") long accountId, @Param("now") Instant now);

    /** 锁定申请人账号，始终先于 Tenant 和 Member 行锁。 */
    TenantProjectionAccount lockAccount(@Param("id") long id);

    /** 读取来源当前投影。 */
    TenantProjection find(@Param("ownerRefId") long ownerRefId);

    /** 锁定唯一来源的当前投影。 */
    TenantProjection lockTenant(@Param("ownerRefId") long ownerRefId);

    /** 读取初始 OWNER 身份；多 OWNER 会在本期入口失败关闭。 */
    java.util.List<TenantProjectionMember> owners(@Param("tenantId") long tenantId);

    /** 原子创建第一版 PENDING 来源投影。 */
    int insertTenant(@Param("id") long id, @Param("ownerRefId") long ownerRefId, @Param("hash") String hash, @Param("now") Instant now);

    /** 保存真实申请人作为 OWNER；状态不由后续投影复位。 */
    int insertMember(@Param("id") long id, @Param("tenantId") long tenantId, @Param("accountId") long accountId, @Param("now") Instant now);

    /** 为该 Tenant 创建具名系统角色。 */
    int insertRole(@Param("id") long id, @Param("tenantId") long tenantId, @Param("code") String code, @Param("now") Instant now);

    /** 记录初始 OWNER 自身的角色授权来源。 */
    int insertGrant(@Param("id") long id, @Param("tenantId") long tenantId, @Param("memberId") long memberId, @Param("roleId") long roleId, @Param("now") Instant now);

    /** 仅读取本 Tenant 的正式 Owner 系统角色标识。 */
    Long ownerRole(@Param("tenantId") long tenantId);

    /** 批准时撤销该 OWNER 的临时入驻授权，保留历史记录。 */
    int revokeOnboarding(@Param("tenantId") long tenantId, @Param("memberId") long memberId, @Param("now") Instant now);

    /** 同时校验来源序号和本地并发版本，且不修改 security_status。 */
    int updateStatus(@Param("id") long id, @Param("version") int version, @Param("previousSourceVersion") int previousSourceVersion, @Param("sourceVersion") int sourceVersion, @Param("hash") String hash, @Param("status") OperationsTenantOwnerStatus status, @Param("now") Instant now);

    /** 按编码读取当前 ACTIVE 的权限主键，用于首次 OWNER 授权。 */
    java.util.List<Long> activePermissionIds(@Param("codes") java.util.Collection<String> codes);

    /** 为正式 OWNER 角色写入一项 ACTIVE 权限。 */
    int insertRolePermission(@Param("id") long id, @Param("roleId") long roleId, @Param("permissionId") long permissionId,
        @Param("now") Instant now);
}

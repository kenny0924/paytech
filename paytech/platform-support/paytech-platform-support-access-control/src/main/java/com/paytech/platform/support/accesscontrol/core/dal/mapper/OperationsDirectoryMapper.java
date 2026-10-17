package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccountSummary;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMemberRole;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPermission;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRoleEmployeeCount;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsNavigationResourceView;

/** INTERNAL 人员、当前权限和 Console Resource 的本库查询与条件写入。 */
public interface OperationsDirectoryMapper {

    /** 冻结一笔管理命令使用的数据库授权评估时刻。 */
    Instant managementAuthorizationTime();

    List<OperationsAccount> accounts(@Param("offset") int offset, @Param("size") int size);

    List<OperationsAccountSummary> accountSummaries(@Param("offset") int offset, @Param("size") int size);

    long accountCount();

    List<OperationsRole> roles();

    List<OperationsRole> pagedRoles(@Param("offset") int offset, @Param("size") int size);

    long roleCount();

    /** 不限定 effective_from，未来生效的关联同样阻止角色删除。 */
    List<OperationsRoleEmployeeCount> roleEmployeeCounts(@Param("roleIds") List<Long> roleIds);

    List<OperationsRole> memberRoles(long memberId);

    List<OperationsRole> memberRolesAt(@Param("memberId") long memberId, @Param("effectiveAt") Instant effectiveAt);

    List<OperationsMemberRole> memberRolesForMembers(@Param("memberIds") List<Long> memberIds);

    /** 删除成员时记录全部待撤销的角色，包括未来生效及尚未清理的过期关联。 */
    List<Long> activeMemberRoleIds(long memberId);

    /** 可能被本次命令减少的当前或未来 PLATFORM_ADMIN；排除在 cutoff 前已过期的授权。 */
    List<Long> activeOrFutureAdministratorRoleIds(@Param("memberId") long memberId,
        @Param("effectiveAt") Instant effectiveAt);

    List<String> permissions(long memberId);

    List<String> permissionsAt(@Param("memberId") long memberId, @Param("effectiveAt") Instant effectiveAt);

    String routePermission(String routeCode);

    com.paytech.platform.support.api.enums.GatewayAuthorizationMode routeAuthorizationMode(String routeCode);

    List<OperationsNavigationResourceView> resources(long memberId);

    long activeAdministratorCount();

    long activeAdministratorCountAt(@Param("effectiveAt") Instant effectiveAt);

    boolean contributesActiveAdministrator(@Param("accountId") long accountId);

    boolean contributesActiveAdministratorAt(@Param("accountId") long accountId,
        @Param("effectiveAt") Instant effectiveAt);

    long humanAccountCount();

    int advanceMember(@Param("id") long id, @Param("version") int version, @Param("now") Instant now);

    void revokeRoles(@Param("memberId") long memberId, @Param("now") Instant now);

    int grantRole(@Param("id") long id, @Param("memberId") long memberId, @Param("tenantId") long tenantId,
        @Param("roleId") long roleId, @Param("actorId") long actorId, @Param("now") Instant now);

    int restoreJustRevokedRole(@Param("memberId") long memberId, @Param("tenantId") long tenantId,
        @Param("roleId") long roleId, @Param("actorId") long actorId, @Param("now") Instant now);

    OperationsRole role(long roleId);

    boolean roleInOperationsScope(long roleId);

    Long lockRole(@Param("id") long id, @Param("exclusive") boolean exclusive);

    long existingRoleCode(@Param("tenantId") long tenantId, @Param("code") String code);

    List<Long> rolePermissionIds(long roleId);

    List<OperationsPermission> grantablePermissions(long memberId);

    List<OperationsPermission> grantablePermissionsAt(@Param("memberId") long memberId,
        @Param("effectiveAt") Instant effectiveAt);

    int insertRole(@Param("id") long id, @Param("tenantId") long tenantId, @Param("code") String code,
        @Param("name") String name, @Param("now") Instant now);

    int updateRole(@Param("id") long id, @Param("tenantId") long tenantId, @Param("name") String name,
        @Param("version") int version, @Param("now") Instant now);

    int deleteRole(@Param("id") long id, @Param("tenantId") long tenantId, @Param("version") int version,
        @Param("now") Instant now);

    void revokeRolePermissions(@Param("roleId") long roleId, @Param("now") Instant now);

    int grantPermission(@Param("id") long id, @Param("roleId") long roleId, @Param("permissionId") long permissionId,
        @Param("now") Instant now);

    /** 仅撤销本次移除的角色权限关联。 */
    int revokePermission(@Param("roleId") long roleId, @Param("permissionId") long permissionId, @Param("now") Instant now);
}

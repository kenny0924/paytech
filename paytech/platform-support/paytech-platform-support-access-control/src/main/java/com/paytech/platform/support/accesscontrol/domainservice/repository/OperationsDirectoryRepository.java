package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccountSummary;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPermission;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsNavigationResourceView;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;

/** Operations Directory 的本库事实访问接口。 */
public interface OperationsDirectoryRepository {
    /** 读取一次数据库时钟，供一笔管理命令冻结所有有效期判断。 */
    Instant managementAuthorizationTime();

    List<OperationsAccount> accounts(int offset, int size);

    /** 保留缺失成员/凭据的人员行，调用方必须对 nullable 展示事实失败关闭。 */
    List<OperationsAccountSummary> accountSummaries(int offset, int size);

    long accountCount();

    List<OperationsRole> roles();

    List<OperationsRole> roles(int offset, int size);

    long roleCount();

    /** 一次数据库分组查询，包含当前范围角色的零计数。 */
    Map<Long, Long> roleEmployeeCounts(List<Long> roleIds);

    List<OperationsRole> memberRoles(long memberId);

    List<OperationsRole> memberRolesAt(long memberId, Instant effectiveAt);

    /** 一次读取本页所有成员的有效角色，输入成员无有效角色时对应空列表。 */
    Map<Long, List<OperationsRole>> memberRoles(List<Long> memberIds);

    List<Long> activeMemberRoleIds(long memberId);

    /** 返回 cutoff 后仍可能贡献管理员资格的 ACTIVE PLATFORM_ADMIN 授权。 */
    List<Long> activeOrFutureAdministratorRoleIds(long memberId, Instant effectiveAt);

    List<String> permissions(long memberId);

    List<String> permissionsAt(long memberId, Instant effectiveAt);

    String routePermission(String routeCode);

    com.paytech.platform.support.api.enums.GatewayAuthorizationMode routeAuthorizationMode(String routeCode);

    List<OperationsNavigationResourceView> resources(long memberId);

    long activeAdministratorCount();

    long activeAdministratorCountAt(Instant effectiveAt);

    /** 与有效管理员计数共用资格条件，判断该账号是否实际贡献计数。 */
    boolean contributesActiveAdministrator(long accountId);

    boolean contributesActiveAdministratorAt(long accountId, Instant effectiveAt);

    long humanAccountCount();

    int advanceMember(long id, int version, Instant now);

    void revokeRoles(long memberId, Instant now);

    int grantRole(long id, long memberId, long tenantId, long roleId, long actorId, Instant now);

    int restoreJustRevokedRole(long memberId, long tenantId, long roleId, long actorId, Instant now);

    OperationsRole role(long roleId);

    boolean roleInOperationsScope(long roleId);

    /** 引用角色使用共享锁；修改/删除角色及管理员集合仲裁使用排他锁。 */
    Long lockRole(long id, boolean exclusive);

    long existingRoleCode(long tenantId, String code);

    List<Long> rolePermissionIds(long roleId);

    List<OperationsPermission> grantablePermissions(long memberId);

    List<OperationsPermission> grantablePermissionsAt(long memberId, Instant effectiveAt);

    void insertRole(long id, long tenantId, String code, String name, Instant now);

    int updateRole(long id, long tenantId, String name, int version, Instant now);

    int deleteRole(long id, long tenantId, int version, Instant now);

    void revokeRolePermissions(long roleId, Instant now);

    void grantPermission(long id, long roleId, long permissionId, Instant now);

    /** 仅撤销本次移除的角色权限关联。 */
    void revokePermission(long roleId, long permissionId, Instant now);
}

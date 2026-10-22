package com.paytech.platform.support.accesscontrol.domainservice.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;

/** Operations 人员和角色管理共用的当前权限、局部锁与管理员保护规则。 */
public interface OperationsManagementPolicyService {

    /** 在调用方事务内冻结授权时点、取得所需数据库保护并完成权限重检。 */
    ManagementContext authorizeManagement(String actorId, String sessionId, Long targetId, List<String> nextRoles,
        Long editedRole, Long replayRole, boolean removingRoles, String... permissions);

    /** 判断当前角色是否为平台范围的内置管理员角色。 */
    boolean isPlatformAdministrator(OperationsRole role);

    /** 重检当前普通 Session，至少满足一项所需权限。 */
    OperationsSessionService.SessionContext requirePermission(String accountId, String sessionId,
        String... permissions);

    /** 判断已验证的当前普通 Session 是否持有某项权限，不抛出拒绝；用于“持有则放宽范围”的场景。 */
    boolean hasPermission(OperationsSessionService.SessionContext context, String permission);

    /** 锁定并重检已完成初始改密的 Bootstrap 身份，供首次管理员交接事务使用。 */
    OperationsSessionService.SessionContext requireBootstrapAdministrator(String actorId, String sessionId);

    /** 校验角色数量、唯一性及当前 Operations 范围，保留调用方给出的顺序。 */
    List<Long> validateRoles(List<String> values);

    /** 读取当前成员可授予的有效权限集合。 */
    Set<Long> grantablePermissionIds(long memberId);

    /** 读取本次管理命令冻结时点内可授予的权限集合。 */
    Set<Long> grantablePermissionIds(ManagementContext context);

    /** 拒绝超出当前操作者授权上限的权限。 */
    void requireGrantablePermissions(List<Long> permissions, Set<Long> allowed);

    /** 每个待授角色的当前权限必须落在操作者可授集合内。 */
    void requireGrantableRoles(ManagementContext context, List<Long> roleIds);

    /** 仅当目标当前贡献有效管理员计数且将失去资格时保护最后管理员。 */
    void protectLastAdministrator(ManagementContext context, long accountId, List<Long> nextRoles);

    /** 恢复本事务刚撤销的同微秒授权，或创建新授权；非预期写入数视为并发冲突。 */
    void grant(long member, long tenant, long role, long actor, Instant now);

    /** 复用 Session 能力对稳定正整数 ID 的解析契约。 */
    long parseId(String value);

    /** 管理命令复用的不可变授权上下文；对业务代码保留常用 Account/Member 访问器。 */
    record ManagementContext(OperationsSessionService.SessionContext subject, Instant effectiveAt) {
        public OperationsAccount account() {
            return subject.account();
        }

        public OperationsMember member() {
            return subject.member();
        }
    }

    /** 用于角色选择器的 ACTIVE 目录；平台管理员具备显式首次授权资格。 */
    List<com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission>
        grantablePermissions(long memberId);

    /** 首次授权的菜单树包含尚未启用的 Operations 配置，不隐式补父权限。 */
    List<com.paytech.platform.support.accesscontrol.domain.operations.OperationsManagementModels.RoleResource>
        grantableResources(long memberId);

    /** 原 INACTIVE 关联可保留，任何新增关联必须读取并锁定 ACTIVE 权限。 */
    void requireActiveNewPermissions(List<Long> next, List<Long> previous);
}

package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConsoleResourceCapabilities;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsManagementModels.RoleResource;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ConfigurationCatalogRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsDirectoryRepository;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsIdentityRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService;
import com.paytech.platform.support.api.enums.OperationsAccountType;
import com.paytech.platform.support.api.enums.OperationsErrorCode;

import lombok.AllArgsConstructor;

/** 人员与角色命令共享的授权上限、数据库锁及管理员不变量。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsManagementPolicyServiceImpl implements OperationsManagementPolicyService {
    private final IdGenerator ids;
    private final OperationsSessionService sessions;
    private final OperationsIdentityRepository identities;
    private final OperationsDirectoryRepository directory;
    private final ConfigurationCatalogRepository catalog;

    @Override
    public ManagementContext authorizeManagement(String actorId, String sessionId, Long targetId,
        List<String> nextRoles, Long editedRole, Long replayRole, boolean removingRoles, String... permissions) {
        List<Long> requested = roleLockShape(nextRoles);
        requireOperationsRoleScope(requested, editedRole, replayRole);
        long actor = sessions.parseId(actorId);
        if (identities.lockManagementActor(actor) == null || identities.lockManagementMember(actor) == null
            || identities.internalTenant() == null) {
            throw new FailException(OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        }
        Instant effectiveAt = directory.managementAuthorizationTime();
        lockAuthorizationRoles(actor, targetId, requested, editedRole, replayRole, removingRoles, effectiveAt);
        var current = sessions.requireCurrent(actorId, sessionId);
        sessions.requireOrdinary(current);
        requireAnyPermission(directory.permissionsAt(current.member().id(), effectiveAt), permissions);
        return new ManagementContext(current, effectiveAt);
    }

    private void lockAuthorizationRoles(long actorId, Long targetId, List<Long> requested, Long editedRole,
        Long replayRole, boolean removingRoles, Instant effectiveAt) {
        var modes = new TreeMap<Long, Boolean>();
        currentMemberRoleIds(actorId, effectiveAt).forEach(id -> modes.put(id, false));
        if (targetId != null && !removingRoles) {
            memberRoleIds(targetId).forEach(id -> modes.put(id, false));
        }
        requested.forEach(id -> modes.put(id, false));
        if (replayRole != null) {
            modes.put(replayRole, false);
        }
        if (editedRole != null) {
            modes.put(editedRole, true);
        }
        // 先决定最强模式再按 ID 加锁，避免先共享后升级；只串行化可能减少管理员的命令。
        if (removingRoles) {
            OperationsMember target = identities.member(targetId);
            if (target != null) {
                directory.activeOrFutureAdministratorRoleIds(target.id(), effectiveAt).stream()
                    .filter(id -> !requested.contains(id)).forEach(id -> modes.put(id, true));
            }
        }
        modes.forEach(directory::lockRole);
    }

    private List<Long> roleLockShape(List<String> values) {
        if (values == null || values.size() > 20) {
            throw new InvalidParameterException("一次最多选择 20 个角色");
        }
        List<Long> roleIds = values.stream().map(sessions::parseId).distinct().toList();
        if (roleIds.size() != values.size()) {
            throw new InvalidParameterException("角色列表不能包含重复角色");
        }
        return roleIds;
    }

    private void requireOperationsRoleScope(List<Long> requested, Long editedRole, Long replayRole) {
        if (requested.isEmpty() && editedRole == null && replayRole == null) {
            return;
        }
        Set<Long> allowed = directory.roles().stream().map(OperationsRole::id).collect(Collectors.toSet());
        if (!allowed.containsAll(requested)
            || editedRole != null && !directory.roleInOperationsScope(editedRole)
            || replayRole != null && !directory.roleInOperationsScope(replayRole)) {
            throw new BizCheckFailException("所选角色不存在或当前不可用");
        }
    }

    private List<Long> memberRoleIds(long accountId) {
        OperationsMember member = identities.member(accountId);
        return member == null ? List.of() : directory.activeMemberRoleIds(member.id());
    }

    private List<Long> currentMemberRoleIds(long accountId, Instant effectiveAt) {
        OperationsMember member = identities.member(accountId);
        return member == null ? List.of()
            : directory.memberRolesAt(member.id(), effectiveAt).stream().map(OperationsRole::id).toList();
    }

    @Override
    public boolean isPlatformAdministrator(OperationsRole role) {
        return role.systemRole() && role.platformRole() && role.code().equals("PLATFORM_ADMIN");
    }

    @Override
    public OperationsSessionService.SessionContext requirePermission(String accountId, String sessionId,
        String... permissions) {
        var current = sessions.requireCurrent(accountId, sessionId);
        sessions.requireOrdinary(current);
        requireAnyPermission(directory.permissions(current.member().id()), permissions);
        return current;
    }

    @Override
    public boolean hasPermission(OperationsSessionService.SessionContext context, String permission) {
        return context.member() != null && directory.permissions(context.member().id()).contains(permission);
    }

    private void requireAnyPermission(List<String> granted, String... permissions) {
        if (Arrays.stream(permissions).noneMatch(granted::contains)) {
            throw new FailException(OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        }
    }

    @Override
    public OperationsSessionService.SessionContext requireBootstrapAdministrator(String actorId, String sessionId) {
        var current = sessions.requireCurrent(actorId, sessionId);
        if (current.account().type() != OperationsAccountType.BOOTSTRAP_ADMIN || current.password().forceChange()) {
            throw new BizCheckFailException("只有已修改初始密码的初始化账号才能创建首次管理员");
        }
        return current;
    }

    @Override
    public List<Long> validateRoles(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > 20) {
            throw new InvalidParameterException("必须选择 1 至 20 个角色");
        }
        List<Long> roleIds = values.stream().map(sessions::parseId).distinct().toList();
        Set<Long> allowed = directory.roles().stream().map(OperationsRole::id).collect(Collectors.toSet());
        if (roleIds.size() != values.size()) {
            throw new InvalidParameterException("角色列表不能包含重复角色");
        }
        if (!allowed.containsAll(roleIds)) {
            throw new BizCheckFailException("所选角色不存在或当前不可用");
        }
        return roleIds;
    }

    @Override
    public Set<Long> grantablePermissionIds(long memberId) {
        return delegable(memberId, directory.managementAuthorizationTime(), true).stream()
            .map(Permission::permissionId).collect(Collectors.toSet());
    }

    @Override
    public Set<Long> grantablePermissionIds(ManagementContext context) {
        var candidates = delegable(context.member().id(), context.effectiveAt(), true);
        var locked = candidates.stream().map(Permission::permissionId).sorted().toList();
        locked.forEach(id -> catalog.lockPermission(id, false));
        locked.forEach(catalog::lockOperationsReferences);
        // 新出现但未锁定的候选本次不授予，防止重查扩大到尚未冻结的引用。
        return delegable(context.member().id(), context.effectiveAt(), true).stream()
            .map(Permission::permissionId).filter(locked::contains).collect(Collectors.toSet());
    }

    @Override
    public void requireGrantablePermissions(List<Long> permissions, Set<Long> allowed) {
        if (!allowed.containsAll(permissions)) {
            throw new FailException(OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        }
    }

    @Override
    public void requireGrantableRoles(ManagementContext context, List<Long> roleIds) {
        Set<Long> allowed = grantablePermissionIds(context);
        for (Long roleId : roleIds) {
            requireGrantablePermissions(directory.rolePermissionIds(roleId), allowed);
        }
    }

    @Override
    public void protectLastAdministrator(ManagementContext context, long accountId, List<Long> nextRoles) {
        OperationsMember member = identities.member(accountId);
        if (member == null) {
            throw new BizCheckFailException("人员账号没有可管理的成员关系");
        }
        if (!directory.contributesActiveAdministratorAt(accountId, context.effectiveAt())) {
            return;
        }
        var administrator = directory.memberRolesAt(member.id(), context.effectiveAt()).stream()
            .filter(this::isPlatformAdministrator).findFirst();
        if (administrator.isPresent() && !nextRoles.contains(administrator.get().id())
            && directory.activeAdministratorCountAt(context.effectiveAt()) <= 1) {
            throw new BizCheckFailException("不能移除最后一个有效管理员");
        }
    }

    @Override
    public void grant(long member, long tenant, long role, long actor, Instant now) {
        // 固定时钟或同一微秒内完整替换角色时，优先恢复本事务刚撤销的同一授权。
        if (directory.restoreJustRevokedRole(member, tenant, role, actor, now) == 1) {
            return;
        }
        if (directory.grantRole(ids.nextId(), member, tenant, role, actor, now) != 1) {
            throw new ConcurrentRequestException("数据已被更新，请刷新后重试");
        }
    }

    @Override
    public long parseId(String value) {
        return sessions.parseId(value);
    }

    @Override
    public List<Permission> grantablePermissions(long memberId) {
        return delegable(memberId, directory.managementAuthorizationTime(), false);
    }

    @Override
    public List<RoleResource> grantableResources(long memberId) {
        Instant at = directory.managementAuthorizationTime();
        Set<Long> allowed = delegable(memberId, at, false).stream().map(Permission::permissionId).collect(Collectors.toSet());
        if (!administrator(memberId, at)) {
            return directory.resources(memberId).stream().filter(r -> allowed.contains(r.getPermissionId()))
                .map(r -> new RoleResource(Long.toString(r.getId()), r.getCode(), r.getType().getCode(),
                    r.getParentId() == null ? null : r.getParentId().toString(), r.getDisplayName(), r.getFrontendRoute(),
                    r.getComponentKey(), Long.toString(r.getPermissionId()), r.getSortOrder())).toList();
        }
        var resources = catalog.resources(ConsoleResourceCapabilities.MAX_RESOURCES + 1);
        if (resources.size() > ConsoleResourceCapabilities.MAX_RESOURCES) throw new BizCheckFailException("Operations 资源超过 2000 条");
        return resources.stream().filter(r -> allowed.contains(r.getPermissionId()))
            .map(r -> new RoleResource(Long.toString(r.getResourceId()), r.getResourceCode(),
                r.getResourceType().getCode(),
                r.getParentResourceId() == null ? null : r.getParentResourceId().toString(), r.getDisplayName(),
                r.getFrontendRoute(), r.getComponentKey(), Long.toString(r.getPermissionId()), r.getSortOrder()))
            .toList();
    }

    @Override
    public void requireActiveNewPermissions(List<Long> next, List<Long> previous) {
        next.stream().sorted().forEach(id -> {
            Permission permission = catalog.lockPermission(id, false);
            if (permission == null || permission.status() != PermissionStatus.ACTIVE && !previous.contains(id)) {
                throw new BizCheckFailException("不能新增停用或不存在的权限关联");
            }
        });
    }

    /** 平台管理员仍须是当前有效的精确内置角色，不按名字或权限数量猜测。 */
    private boolean administrator(long memberId, Instant at) {
        return directory.memberRolesAt(memberId, at).stream().anyMatch(this::isPlatformAdministrator);
    }

    /** 首次授权只放宽 Operations 配置引用内的委派范围，不形成业务权限通配。 */
    private List<Permission> delegable(long memberId, Instant at, boolean includeInactive) {
        return catalog.delegablePermissions(memberId, at, administrator(memberId, at), includeInactive);
    }
}

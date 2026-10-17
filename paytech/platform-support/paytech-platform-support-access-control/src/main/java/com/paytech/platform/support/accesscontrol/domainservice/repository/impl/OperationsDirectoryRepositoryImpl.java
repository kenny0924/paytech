package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccountSummary;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPermission;
import com.paytech.platform.support.accesscontrol.domain.operations.OperationsNavigationResourceView;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRoleEmployeeCount;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsDirectoryMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsDirectoryRepository;

import lombok.AllArgsConstructor;

/** 访问 Owner JAR 内的 Operations 目录事实及批量查询投影。 */
@Repository
@AllArgsConstructor
public class OperationsDirectoryRepositoryImpl implements OperationsDirectoryRepository {
    private final OperationsDirectoryMapper mapper;

    @Override
    public Instant managementAuthorizationTime() {
        return mapper.managementAuthorizationTime();
    }

    @Override
    public Long lockRole(long id, boolean exclusive) {
        return mapper.lockRole(id, exclusive);
    }

    @Override
    public List<OperationsAccount> accounts(int offset, int size) {
        return mapper.accounts(offset, size);
    }

    @Override
    public List<OperationsAccountSummary> accountSummaries(int offset, int size) {
        return mapper.accountSummaries(offset, size);
    }

    @Override
    public long accountCount() {
        return mapper.accountCount();
    }

    @Override
    public List<OperationsRole> roles() {
        return mapper.roles();
    }

    @Override
    public List<OperationsRole> roles(int offset, int size) {
        return mapper.pagedRoles(offset, size);
    }

    @Override
    public long roleCount() {
        return mapper.roleCount();
    }

    @Override
    public Map<Long, Long> roleEmployeeCounts(List<Long> roleIds) {
        return mapper.roleEmployeeCounts(roleIds).stream().collect(Collectors
            .toUnmodifiableMap(OperationsRoleEmployeeCount::roleId, OperationsRoleEmployeeCount::employeeCount));
    }

    @Override
    public List<OperationsRole> memberRoles(long memberId) {
        return mapper.memberRoles(memberId);
    }

    @Override
    public List<OperationsRole> memberRolesAt(long memberId, Instant effectiveAt) {
        return mapper.memberRolesAt(memberId, effectiveAt);
    }

    @Override
    public Map<Long, List<OperationsRole>> memberRoles(List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<OperationsRole>> grouped = new java.util.LinkedHashMap<>();
        memberIds.forEach(memberId -> grouped.put(memberId, new java.util.ArrayList<>()));
        mapper.memberRolesForMembers(memberIds).forEach(row -> grouped.get(row.memberId()).add(row.role()));
        grouped.replaceAll((memberId, roles) -> List.copyOf(roles));
        return Map.copyOf(grouped);
    }

    @Override
    public List<Long> activeMemberRoleIds(long memberId) {
        return mapper.activeMemberRoleIds(memberId);
    }

    @Override
    public List<Long> activeOrFutureAdministratorRoleIds(long memberId, Instant effectiveAt) {
        return mapper.activeOrFutureAdministratorRoleIds(memberId, effectiveAt);
    }

    @Override
    public List<String> permissions(long memberId) {
        return mapper.permissions(memberId);
    }

    @Override
    public List<String> permissionsAt(long memberId, Instant effectiveAt) {
        return mapper.permissionsAt(memberId, effectiveAt);
    }

    @Override
    public String routePermission(String routeCode) {
        return mapper.routePermission(routeCode);
    }

    @Override
    public com.paytech.platform.support.api.enums.GatewayAuthorizationMode routeAuthorizationMode(String routeCode) {
        return mapper.routeAuthorizationMode(routeCode);
    }

    @Override
    public List<OperationsNavigationResourceView> resources(long memberId) {
        return mapper.resources(memberId);
    }

    @Override
    public long activeAdministratorCount() {
        return mapper.activeAdministratorCount();
    }

    @Override
    public long activeAdministratorCountAt(Instant effectiveAt) {
        return mapper.activeAdministratorCountAt(effectiveAt);
    }

    @Override
    public boolean contributesActiveAdministrator(long accountId) {
        return mapper.contributesActiveAdministrator(accountId);
    }

    @Override
    public boolean contributesActiveAdministratorAt(long accountId, Instant effectiveAt) {
        return mapper.contributesActiveAdministratorAt(accountId, effectiveAt);
    }

    @Override
    public long humanAccountCount() {
        return mapper.humanAccountCount();
    }

    @Override
    public int advanceMember(long id, int version, Instant now) {
        return mapper.advanceMember(id, version, now);
    }

    @Override
    public void revokeRoles(long memberId, Instant now) {
        mapper.revokeRoles(memberId, now);
    }

    @Override
    public int grantRole(long id, long memberId, long tenantId, long roleId, long actorId, Instant now) {
        return mapper.grantRole(id, memberId, tenantId, roleId, actorId, now);
    }

    @Override
    public int restoreJustRevokedRole(long memberId, long tenantId, long roleId, long actorId, Instant now) {
        return mapper.restoreJustRevokedRole(memberId, tenantId, roleId, actorId, now);
    }

    @Override
    public OperationsRole role(long roleId) {
        return mapper.role(roleId);
    }

    @Override
    public boolean roleInOperationsScope(long roleId) {
        return mapper.roleInOperationsScope(roleId);
    }

    @Override
    public long existingRoleCode(long tenantId, String code) {
        return mapper.existingRoleCode(tenantId, code);
    }

    @Override
    public List<Long> rolePermissionIds(long roleId) {
        return mapper.rolePermissionIds(roleId);
    }

    @Override
    public List<OperationsPermission> grantablePermissions(long memberId) {
        return mapper.grantablePermissions(memberId);
    }

    @Override
    public List<OperationsPermission> grantablePermissionsAt(long memberId, Instant effectiveAt) {
        return mapper.grantablePermissionsAt(memberId, effectiveAt);
    }

    @Override
    public void insertRole(long id, long tenantId, String code, String name, Instant now) {
        mapper.insertRole(id, tenantId, code, name, now);
    }

    @Override
    public int updateRole(long id, long tenantId, String name, int version, Instant now) {
        return mapper.updateRole(id, tenantId, name, version, now);
    }

    @Override
    public int deleteRole(long id, long tenantId, int version, Instant now) {
        return mapper.deleteRole(id, tenantId, version, now);
    }

    @Override
    public void revokeRolePermissions(long roleId, Instant now) {
        mapper.revokeRolePermissions(roleId, now);
    }

    @Override
    public void grantPermission(long id, long roleId, long permissionId, Instant now) {
        mapper.grantPermission(id, roleId, permissionId, now);
    }

    /** 仅撤销被移除的权限，保留 INACTIVE 权限的既有未撤销关联。 */
    @Override
    public void revokePermission(long roleId, long permissionId, Instant now) {
        mapper.revokePermission(roleId, permissionId, now);
    }
}

package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsDirectoryMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsRolePermissionRepository;

import lombok.AllArgsConstructor;

/** 只写角色权限表，角色聚合 CAS 由同事务的 Service 先完成。 */
@Repository
@AllArgsConstructor
public class OperationsRolePermissionRepositoryImpl implements OperationsRolePermissionRepository {
    private final OperationsDirectoryMapper mapper;

    /** 批量撤销允许零关联，不伪造受影响行数。 */
    @Override
    public void revokeAll(long roleId, Instant now) { mapper.revokeRolePermissions(roleId, now); }

    /** 撤销被完整集合明确移除的关联。 */
    @Override
    public void revoke(long roleId, long permissionId, Instant now) {
        requireRows(mapper.revokePermission(roleId, permissionId, now), false);
    }

    /** 沿用数据库唯一关联的插入或恢复语义。 */
    @Override
    public void grant(long id, long roleId, long permissionId, Instant now) {
        requireRows(mapper.grantPermission(id, roleId, permissionId, now), true);
    }

    /** MySQL 恢复已存在关联时影响两行计数；其他非预期结果使事务回滚。 */
    void requireRows(int rows, boolean upsert) {
        if (rows != 1 && !(upsert && rows == 2)) {
            throw new com.paytech.foundation.common.error.ConcurrentRequestException("角色权限关联已变化，请刷新后重试");
        }
    }
}

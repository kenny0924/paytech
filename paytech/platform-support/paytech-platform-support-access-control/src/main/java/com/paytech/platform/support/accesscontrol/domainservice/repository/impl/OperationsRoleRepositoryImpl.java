package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRole;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsDirectoryMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsRoleRepository;

import lombok.AllArgsConstructor;

/** 条件写成功后同步原对象；事务由完整用例控制。 */
@Repository
@AllArgsConstructor
public class OperationsRoleRepositoryImpl implements OperationsRoleRepository {
    private final OperationsDirectoryMapper mapper;
    /** 读取当前可授予的角色目录。 */
    @Override
    public List<OperationsRole> roles() {
        return mapper.roles();
    }

    /** 受唯一角色代码约束保护，不转换无关重复键。 */
    @Override
    public void insert(OperationsRole role, long tenantId, Instant now) {
        requireOne(mapper.insertRole(role.id(), tenantId, role.code(), role.name(), now));
    }

    /** 成功后同步名称及聚合版本，失败保留原对象。 */
    @Override
    public void update(OperationsRole role, long tenantId, String name, Instant now) {
        requireOne(mapper.updateRole(role.id(), tenantId, name, role.version(), now));
        role.name(name);
        role.version(role.version() + 1);
    }

    /** 成功后同步版本；删除上下文不得再次用于写入。 */
    @Override
    public void delete(OperationsRole role, long tenantId, Instant now) {
        requireOne(mapper.deleteRole(role.id(), tenantId, role.version(), now));
        role.status(com.paytech.platform.support.accesscontrol.domain.enums.TenantRoleStatus.INACTIVE);
        role.version(role.version() + 1);
    }

    /** CAS 或插入必须影响恰好一行。 */
    void requireOne(int rows) {
        if (rows != 1) {
            throw new ConcurrentRequestException("数据已被更新，请刷新后重试");
        }
    }
}

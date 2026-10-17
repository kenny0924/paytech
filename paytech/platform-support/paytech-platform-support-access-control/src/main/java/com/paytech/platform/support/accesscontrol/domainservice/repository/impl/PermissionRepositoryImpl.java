package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ConfigurationCatalogMapper;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.PermissionFilter;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsPermissionRiskLevel;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.PermissionRepository;

import lombok.AllArgsConstructor;

/** 复用当前权限 SQL，影响行数确认后回写原对象。 */
@Repository
@AllArgsConstructor
public class PermissionRepositoryImpl implements PermissionRepository {
    private final ConfigurationCatalogMapper mapper;

    @Override
    public List<Permission> permissions(PermissionFilter filter, int offset, int size) {
        return mapper.permissions(filter, offset, size);
    }
    @Override
    public long count(PermissionFilter filter) { return mapper.permissionCount(filter); }
    @Override
    public Permission permission(String code) { return mapper.permission(code); }
    @Override
    public Permission lock(long permissionId) { return mapper.lockPermission(permissionId, true); }

    /** 只转换两个已知业务唯一键，其他故障保留原异常。 */
    @Override
    public void insert(Permission permission) {
        try {
            requireSaved(mapper.insertPermission(permission));
        } catch (DuplicateKeyException failure) {
            String cause = String.valueOf(failure.getMostSpecificCause().getMessage());
            if (!(cause.contains("uk_permission") || cause.contains("uk_resource_action"))) throw failure;
            throw new BizCheckFailException("配置编码或业务身份已经存在", failure);
        }
    }

    @Override
    public void update(Permission permission, OperationsPermissionRiskLevel riskLevel, PermissionStatus status,
        Instant updateTime) {
        if (permission.version() == Integer.MAX_VALUE) {
            throw new ConcurrentRequestException("权限配置版本已达到上限");
        }
        requireSaved(mapper.updatePermission(permission, riskLevel, status, updateTime));
        permission.riskLevel(riskLevel);
        permission.status(status);
        permission.version(permission.version() + 1);
        permission.updateTime(updateTime);
    }

    /** 未精确保存一行时回滚，由调用方丢弃本次 Context。 */
    private void requireSaved(int changed) {
        if (changed != 1) throw new ConcurrentRequestException("权限配置已被其他操作修改，请刷新后重试");
    }
}

package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ConsoleResource;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ConsoleResourceMapper;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ConsoleResourceRepository;

import lombok.AllArgsConstructor;

/** 校验影响行数后回写原资源，失败不推进内存状态。 */
@Repository
@AllArgsConstructor
public class ConsoleResourceRepositoryImpl implements ConsoleResourceRepository {
    private final ConsoleResourceMapper mapper;

    /** 返回本表当前资源。 */
    @Override
    public ConsoleResource resource(String code) {
        return mapper.resource(code);
    }

    /** 保持原 Channel、排序和有界范围读取。 */
    @Override
    public List<ConsoleResource> resources(int limit) {
        return mapper.resources(limit);
    }

    /** 只将资源编码唯一冲突转换为业务拒绝，其他约束失败原样传播。 */
    @Override
    public void insert(ConsoleResource resource) {
        try {
            requireSaved(mapper.insert(resource));
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_console_resource_code")) {
                throw failure;
            }
            throw new BizCheckFailException("配置编码或业务身份已经存在", failure);
        }
    }

    /** CAS 成功后同步全部已保存可变字段、版本和时间，回滚后调用方丢弃 Context。 */
    @Override
    public void update(ConsoleResource resource, String displayName, Long parentResourceId, String frontendRoute,
        String componentKey, long permissionId, int sortOrder, GatewayConfigurationStatus status, Instant updateTime) {
        resource.requireVersion(resource.getVersion());
        if (resource.getVersion() == Integer.MAX_VALUE) {
            throw new ConcurrentRequestException("菜单资源版本已达到上限");
        }
        requireSaved(mapper.update(resource, displayName, parentResourceId, frontendRoute, componentKey, permissionId,
            sortOrder, status, updateTime));
        resource.setDisplayName(displayName);
        resource.setParentResourceId(parentResourceId);
        resource.setFrontendRoute(frontendRoute);
        resource.setComponentKey(componentKey);
        resource.setPermissionId(permissionId);
        resource.setSortOrder(sortOrder);
        resource.setStatus(status);
        resource.setVersion(resource.getVersion() + 1);
        resource.setUpdateTime(updateTime);
    }

    /** 插入和条件更新都必须恰好影响一行。 */
    private void requireSaved(int changed) {
        if (changed != 1) {
            throw new ConcurrentRequestException("菜单资源已被其他操作修改，请刷新后重试");
        }
    }
}

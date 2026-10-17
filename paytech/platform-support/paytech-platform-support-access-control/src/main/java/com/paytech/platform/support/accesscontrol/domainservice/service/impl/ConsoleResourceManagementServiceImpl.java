package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ChangeConsoleResourceStatusContext;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.CreateConsoleResourceContext;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.UpdateConsoleResourceContext;
import com.paytech.platform.support.accesscontrol.domainservice.repository.ConsoleResourceRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.ConsoleResourceManagementService;
import lombok.AllArgsConstructor;

/** 只保存当前 Context 的本表事实，事务由所属 Processor 组织。 */
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ConsoleResourceManagementServiceImpl implements ConsoleResourceManagementService {
    private final ConsoleResourceRepository repository;

    /** 唯一编码与候选树已验证，数据库约束继续仲裁并发。 */
    @Override
    public void create(CreateConsoleResourceContext context) {
        repository.insert(context.getResource());
    }

    /** 不重查已确认的关系，保存成功后原对象体现新版本。 */
    @Override
    public void update(UpdateConsoleResourceContext context) {
        repository.update(context.getResource(), context.getDisplayName(), context.getParentResourceId(),
            context.getFrontendRoute(), context.getComponentKey(), context.getPermissionId(), context.getSortOrder(),
            context.getStatus(), context.getUpdateTime());
    }

    /** 只推进目标状态及版本，其他字段使用同一原对象。 */
    @Override
    public void changeStatus(ChangeConsoleResourceStatusContext context) {
        var row = context.getResource();
        repository.update(row, row.getDisplayName(), row.getParentResourceId(), row.getFrontendRoute(),
            row.getComponentKey(), row.getPermissionId(), row.getSortOrder(), context.getStatus(), context.getUpdateTime());
    }
}
